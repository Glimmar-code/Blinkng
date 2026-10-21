import Foundation
import AVFoundation
import Combine

/// Controls one foreground-only WebRTC session. Android's existing signaling
/// schema is reused; no production tables or push credentials are changed.
@MainActor
final class BLINKForegroundCallCoordinator: ObservableObject {
    @Published private(set) var record: BLINKCallRecord?
    @Published private(set) var status = "Not in a call"
    @Published private(set) var connected = false
    @Published private(set) var busy = false
    @Published private(set) var muted = false
    @Published private(set) var cameraOff = false
    @Published private(set) var speaker = false
    @Published var error: String?

    private let auth: BLINKAuthStore
    private let userID: String
    private let api: BLINKCallSignalingAPI?
    private let callAPI: BLINKCallsAPI?
    private(set) var engine: BLINKWebRTCEngine?
    private var loop: Task<Void, Never>?
    private var signalOffset: Int64 = 0
    private var remoteSDP = false
    private var waitingIce: [BLINKCallSignalPayload] = []
    private var isCaller = false
    private var pendingSessionGeneration = 0

    init(auth: BLINKAuthStore, userID: String,
         config: BLINKBackendConfiguration? = .fromBundle()) {
        self.auth = auth
        self.userID = userID
        api = config.map { BLINKCallSignalingAPI(configuration: $0) }
        callAPI = config.map { BLINKCallsAPI(configuration: $0) }
    }

    func outgoing(conversationID: String, calleeID: String, kind: BLINKCallKind) async {
        guard !busy, record == nil, let api, let callAPI else { return }
        guard UUID(uuidString: conversationID) != nil, UUID(uuidString: calleeID) != nil,
              calleeID != userID else {
            error = "A valid one-to-one conversation is required."
            return
        }
        busy = true
        isCaller = true
        error = nil
        status = "Preparing \(kind.title.lowercased()) call"
        defer { busy = false }

        do {
            try await requestMediaPermission(kind: kind)
            let result = try await auth.withValidToken { token in
                try await api.start(conversationID: conversationID, calleeID: calleeID,
                                    kind: kind, token: token)
            }
            record = result
            do {
                try await prepareEngine(record: result, api: api)
                startPolling()
                let offer = try await requireEngine().createDescription(kind: "offer")
                try await sendSignal("offer", payload: offer)
                status = "Calling…"
                // Android notifications are best effort; APNs background ringing
                // is explicitly not configured. Never claim iPhone background calls.
                do {
                    try await auth.withValidToken { token in
                        try await api.notifyAndroid(callID: result.id, token: token)
                    }
                } catch {
                    // Polling and foreground calls may still work without a push.
                }
            } catch {
                await failAndEnd(reason: "setup_failed", via: callAPI, error: error)
            }
        } catch {
            status = "Call unavailable"
            self.error = error.localizedDescription
        }
    }

    func incoming(callID: String) async {
        guard !busy, record == nil, let api, let callAPI else { return }
        busy = true
        isCaller = false
        error = nil
        defer { busy = false }
        do {
            let pending = try await auth.withValidToken { token in
                try await api.call(callID: callID, token: token)
            }
            guard pending.isIncoming(for: userID), pending.status == .ringing else {
                throw BLINKCallError.notAvailable
            }
            try await requestMediaPermission(kind: pending.callType)
            let servers = try await auth.withValidToken { token in
                try await api.relay(callID: pending.id, token: token)
            }
            // Do not acknowledge an incoming call until microphone/camera and
            // authorized TURN are available; otherwise the caller would see
            // a fake connecting status.
            try createEngine(kind: pending.callType, servers: servers, callID: pending.id)
            let accepted = try await auth.withValidToken { token in
                try await api.answer(callID: pending.id, token: token)
            }
            record = accepted
            status = "Connecting…"
            startPolling()
        } catch {
            await failAndEnd(reason: "answer_failed", via: callAPI, error: error)
        }
    }

    private func prepareEngine(record: BLINKCallRecord, api: BLINKCallSignalingAPI) async throws {
        let servers = try await auth.withValidToken { token in
            try await api.relay(callID: record.id, token: token)
        }
        try createEngine(kind: record.callType, servers: servers, callID: record.id)
    }

    private func createEngine(kind: BLINKCallKind, servers: [BLINKIceServer],
                              callID: String) throws {
        guard engine == nil, let rtc = BLINKWebRTCEngine(kind: kind, servers: servers) else {
            throw BLINKCallError.notAvailable
        }
        do {
            try rtc.activate()
            if kind == .video { try rtc.startCamera() }
        } catch {
            rtc.close()
            throw error
        }
        rtc.onIce = { [weak self] payload in
            Task { @MainActor [weak self] in
                guard let self, self.record?.id == callID else { return }
                do { try await self.sendSignal("ice", payload: payload) }
                catch { self.error = "Network issue while exchanging call candidates." }
            }
        }
        rtc.onConnected = { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, self.record?.id == callID else { return }
                self.connected = true
                self.status = "Connected"
                if let api = self.api {
                    do {
                        try await self.auth.withValidToken { token in
                            try await api.connected(callID: callID, token: token)
                        }
                    } catch { /* Peer media connection is authoritative. */ }
                }
            }
        }
        rtc.onDisconnected = { [weak self] in
            Task { @MainActor [weak self] in
                guard let self, self.record?.id == callID else { return }
                self.connected = false
                self.status = "Reconnecting…"
            }
        }
        engine = rtc
    }

    private func sendSignal(_ kind: String, payload: BLINKCallSignalPayload) async throws {
        guard let api, let call = record else { throw BLINKCallError.notAvailable }
        try await auth.withValidToken { token in
            try await api.signal(callID: call.id, senderID: userID, kind: kind,
                                 payload: payload, token: token)
        }
    }

    private func startPolling() {
        loop?.cancel()
        pendingSessionGeneration += 1
        let generation = pendingSessionGeneration
        loop = Task { [weak self] in
            while !Task.isCancelled {
                guard let self, generation == self.pendingSessionGeneration,
                      let active = self.record, let api = self.api else { break }
                do {
                    let signals = try await self.auth.withValidToken { token in
                        try await api.signals(callID: active.id, after: self.signalOffset,
                                              token: token)
                    }
                    for signal in signals.sorted(by: { $0.id < $1.id }) {
                        self.signalOffset = max(self.signalOffset, signal.id)
                        guard signal.callId == active.id, signal.senderId != self.userID else {
                            continue
                        }
                        try await self.process(signal)
                    }
                    let fresh = try await self.auth.withValidToken { token in
                        try await api.call(callID: active.id, token: token)
                    }
                    self.record = fresh
                    if fresh.status.isTerminal {
                        self.status = fresh.status.rawValue.capitalized
                        self.release()
                        break
                    }
                } catch {
                    // Remain on the screen with a safe retry; do not leak SDP,
                    // TURN credentials or user JWT in errors/logs.
                    self.error = "Call connection is unstable. Trying again."
                }
                try? await Task.sleep(for: .seconds(1.5))
            }
        }
    }

    private func process(_ signal: BLINKCallSignal) async throws {
        guard let engine else { return }
        if signal.kind == "offer" || signal.kind == "answer" {
            try await engine.accept(signal.payload, kind: signal.kind)
            remoteSDP = true
            for ice in waitingIce {
                try? await engine.addCandidate(ice)
            }
            waitingIce = []
            if signal.kind == "offer" && !isCaller {
                let answer = try await engine.createDescription(kind: "answer")
                try await sendSignal("answer", payload: answer)
            }
        } else if signal.kind == "ice" {
            if remoteSDP { try await engine.addCandidate(signal.payload) }
            else if waitingIce.count < 100 { waitingIce.append(signal.payload) }
        }
    }

    func end() async {
        loop?.cancel()
        guard let active = record, let api = callAPI else {
            release()
            return
        }
        status = "Ending call…"
        do {
            _ = try await auth.withValidToken { token in
                try await api.end(callID: active.id, token: token)
            }
        } catch {
            self.error = "Call ended locally; the server has not confirmed the hang-up."
        }
        release()
    }

    func toggleMute() {
        muted.toggle()
        engine?.setMuted(muted)
    }

    func toggleVideo() {
        cameraOff.toggle()
        engine?.setCameraEnabled(!cameraOff)
    }

    func toggleSpeaker() {
        speaker.toggle()
        do { try engine?.setSpeaker(speaker) }
        catch { self.error = "Could not change your audio output." }
    }

    private func failAndEnd(reason: String, via api: BLINKCallsAPI, error: Error) async {
        self.error = error.localizedDescription
        if let record {
            do {
                _ = try await auth.withValidToken { token in
                    try await api.end(callID: record.id, token: token)
                }
            } catch { /* The local session still closes. */ }
        }
        status = "Call could not connect"
        release()
    }

    private func release() {
        loop?.cancel()
        loop = nil
        pendingSessionGeneration += 1
        engine?.close()
        engine = nil
        record = nil
        remoteSDP = false
        signalOffset = 0
        waitingIce = []
        connected = false
        muted = false
        cameraOff = false
    }

    private func requireEngine() throws -> BLINKWebRTCEngine {
        guard let engine else { throw BLINKCallError.notAvailable }
        return engine
    }

    /// Media permissions are prompted only in response to a user-initiated
    /// call or answer action, never silently on opening the Messages tab.
    private func requestMediaPermission(kind: BLINKCallKind) async throws {
        let microphone: Bool = await withCheckedContinuation { continuation in
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
        guard microphone else { throw BLINKCallError.server("Microphone access is required for calls.") }
        if kind == .video {
            let video = await AVCaptureDevice.requestAccess(for: .video)
            guard video else { throw BLINKCallError.server("Camera access is required for video calls.") }
        }
    }
}
