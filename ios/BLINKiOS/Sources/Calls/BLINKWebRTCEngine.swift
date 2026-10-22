import Foundation
import AVFoundation
import WebRTC

/// A real RTCPeerConnection backed by a pinned binary framework.
/// This is foreground-only: CallKit/PushKit APNs must be separately verified.
final class BLINKWebRTCEngine: NSObject, RTCPeerConnectionDelegate {
    static let factory: RTCPeerConnectionFactory = {
        RTCInitializeSSL()
        return RTCPeerConnectionFactory(
            encoderFactory: RTCDefaultVideoEncoderFactory(),
            decoderFactory: RTCDefaultVideoDecoderFactory()
        )
    }()

    var onIce: ((BLINKCallSignalPayload) -> Void)?
    var onConnected: (() -> Void)?
    var onDisconnected: (() -> Void)?

    private(set) var localVideo: RTCVideoTrack?
    private(set) var remoteVideo: RTCVideoTrack?
    private(set) var microphoneEnabled = true
    private(set) var cameraEnabled = true
    private var peer: RTCPeerConnection!
    private var audio: RTCAudioTrack?
    private var camera: RTCCameraVideoCapturer?
    private var closed = false

    init?(kind: BLINKCallKind, servers: [BLINKIceServer]) {
        guard servers.contains(where: \.isAuthenticatedTURN) else { return nil }
        super.init()
        let configuration = RTCConfiguration()
        configuration.iceServers = servers.map {
            RTCIceServer(urlStrings: $0.safeURLs, username: $0.username ?? "",
                         credential: $0.credential ?? "")
        }
        configuration.sdpSemantics = .unifiedPlan
        configuration.continualGatheringPolicy = .gatherContinually

        let constraints = RTCMediaConstraints(
            mandatoryConstraints: nil,
            optionalConstraints: ["DtlsSrtpKeyAgreement": kRTCMediaConstraintsValueTrue]
        )
        guard let peer = Self.factory.peerConnection(
            with: configuration, constraints: constraints, delegate: self
        ) else { return nil }
        self.peer = peer

        let audioSource = Self.factory.audioSource(with: RTCMediaConstraints(
            mandatoryConstraints: nil, optionalConstraints: nil
        ))
        let audioTrack = Self.factory.audioTrack(with: audioSource, trackId: "blink_audio")
        peer.add(audioTrack, streamIds: ["blink_call"])
        audio = audioTrack

        if kind == .video {
            let source = Self.factory.videoSource()
            let video = Self.factory.videoTrack(with: source, trackId: "blink_video")
            peer.add(video, streamIds: ["blink_call"])
            localVideo = video
            #if !targetEnvironment(simulator)
            camera = RTCCameraVideoCapturer(delegate: source)
            #endif
        }
    }

    func activate() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .videoChat,
                                options: [.allowBluetooth, .defaultToSpeaker])
        try session.setActive(true)
    }

    func startCamera() throws {
        guard let camera else {
            throw BLINKCallError.notAvailable
        }
        guard let device = RTCCameraVideoCapturer.captureDevices()
            .first(where: { $0.position == .front }) ??
            RTCCameraVideoCapturer.captureDevices().first,
            let format = RTCCameraVideoCapturer.supportedFormats(for: device)
                .first(where: {
                    let dimensions = CMVideoFormatDescriptionGetDimensions($0.formatDescription)
                    return dimensions.width >= 320 && dimensions.width <= 1280
                }) else {
            throw BLINKCallError.notAvailable
        }
        let maxFps = format.videoSupportedFrameRateRanges
            .map { Int($0.maxFrameRate) }.max() ?? 15
        camera.startCapture(with: device, format: format, fps: min(24, maxFps))
    }

    func setMuted(_ muted: Bool) {
        microphoneEnabled = !muted
        audio?.isEnabled = !muted
    }

    func setCameraEnabled(_ enabled: Bool) {
        cameraEnabled = enabled
        localVideo?.isEnabled = enabled
    }

    func setSpeaker(_ speaker: Bool) throws {
        try AVAudioSession.sharedInstance()
            .overrideOutputAudioPort(speaker ? .speaker : .none)
    }

    func createDescription(kind: String) async throws -> BLINKCallSignalPayload {
        guard !closed else { throw BLINKCallError.notAvailable }
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: [
                kRTCMediaConstraintsOfferToReceiveAudio: kRTCMediaConstraintsValueTrue,
                kRTCMediaConstraintsOfferToReceiveVideo: kRTCMediaConstraintsValueTrue
            ],
            optionalConstraints: nil
        )
        let description: RTCSessionDescription = try await withCheckedThrowingContinuation { continuation in
            let callback: (RTCSessionDescription?, Error?) -> Void = { sdp, error in
                if let error { continuation.resume(throwing: error) }
                else if let sdp { continuation.resume(returning: sdp) }
                else { continuation.resume(throwing: BLINKCallError.unexpectedResponse) }
            }
            if kind == "offer" { peer.offer(for: constraints, completionHandler: callback) }
            else { peer.answer(for: constraints, completionHandler: callback) }
        }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            peer.setLocalDescription(description) { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume() }
            }
        }
        return BLINKCallSignalPayload(
            sdp: description.sdp, type: kind
        )
    }

    func accept(_ payload: BLINKCallSignalPayload, kind: String) async throws {
        guard !closed, let sdp = payload.sdp, !sdp.isEmpty,
              ["offer", "answer"].contains(kind) else {
            throw BLINKCallError.unexpectedResponse
        }
        let description = RTCSessionDescription(type: kind == "offer" ? .offer : .answer, sdp: sdp)
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            peer.setRemoteDescription(description) { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume() }
            }
        }
    }

    func addCandidate(_ payload: BLINKCallSignalPayload) async throws {
        guard !closed, let candidate = payload.candidate, !candidate.isEmpty else {
            throw BLINKCallError.unexpectedResponse
        }
        let item = RTCIceCandidate(sdp: candidate, sdpMLineIndex: Int32(payload.sdpMLineIndex ?? 0),
                                   sdpMid: payload.sdpMid)
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            peer.add(item) { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume() }
            }
        }
    }

    func close() {
        guard !closed else { return }
        closed = true
        camera?.stopCapture()
        audio?.isEnabled = false
        localVideo?.isEnabled = false
        peer?.close()
        peer = nil
        do { try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation) }
        catch { /* iOS audio route cleanup is best effort. */ }
    }

    // MARK: RTCPeerConnectionDelegate

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        if let track = stream.videoTracks.first { remoteVideo = track }
    }
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        DispatchQueue.main.async { [weak self] in
            guard let self, !self.closed else { return }
            if newState == .connected || newState == .completed { self.onConnected?() }
            else if newState == .failed || newState == .disconnected || newState == .closed {
                self.onDisconnected?()
            }
        }
    }
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        let payload = BLINKCallSignalPayload(candidate: candidate.sdp,
            sdpMid: candidate.sdpMid, sdpMLineIndex: Int(candidate.sdpMLineIndex))
        DispatchQueue.main.async { [weak self] in
            guard let self, !self.closed else { return }
            self.onIce?(payload)
        }
    }
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}
}
