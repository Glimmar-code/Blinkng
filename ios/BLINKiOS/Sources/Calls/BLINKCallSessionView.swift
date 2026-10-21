import SwiftUI
import WebRTC

/// Experimental foreground media screen. An active server record is not proof
/// of an established WebRTC media connection; only the ICE callback sets Connected.
struct BLINKCallSessionView: View {
    @StateObject private var call: BLINKForegroundCallCoordinator
    let start: Start
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @State private var started = false

    enum Start {
        case outgoing(conversationID: String, calleeID: String, kind: BLINKCallKind)
        case incoming(callID: String)
        case systemAnswered(callID: UUID)
    }

    init(auth: BLINKAuthStore, userID: String, start: Start) {
        _call = StateObject(wrappedValue: BLINKForegroundCallCoordinator(auth: auth, userID: userID))
        self.start = start
    }

    /// Keep exactly the same WebRTC coordinator as the CallKit answer action;
    /// never create a second peer connection for one accepted system call.
    init(coordinator: BLINKForegroundCallCoordinator, callKitID: UUID) {
        _call = StateObject(wrappedValue: coordinator)
        start = .systemAnswered(callID: callKitID)
    }

    private func finish() {
        if case .systemAnswered(let id) = start {
            BLINKCallKitManager.shared.requestEnd(callID: id)
        } else {
            Task { await call.end() }
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Text(call.status)
                    .font(.title2.bold())
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("blinkCallStatus")

                if let problem = call.error {
                    Text(problem)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }

                if let engine = call.engine, call.record?.callType == .video {
                    VStack(spacing: 8) {
                        BLINKRTCVideoRenderer(track: engine.remoteVideo)
                            .frame(height: 240)
                            .overlay(alignment: .bottom) {
                                if !call.connected {
                                    Text("Waiting for remote video…")
                                        .font(.footnote)
                                        .padding(8)
                                        .background(.regularMaterial)
                                }
                            }
                        BLINKRTCVideoRenderer(track: engine.localVideo)
                            .frame(height: 100)
                    }
                } else {
                    Image(systemName: call.connected ? "waveform.circle.fill" : "phone.arrow.up.right")
                        .font(.system(size: 68))
                        .foregroundStyle(.primary)
                        .accessibilityHidden(true)
                }

                HStack(spacing: 14) {
                    Button {
                        call.toggleMute()
                    } label: {
                        Label(call.muted ? "Unmute" : "Mute",
                              systemImage: call.muted ? "mic.slash" : "mic")
                    }
                    Button {
                        call.toggleSpeaker()
                    } label: {
                        Label(call.speaker ? "Earpiece" : "Speaker",
                              systemImage: "speaker.wave.2")
                    }
                    if call.record?.callType == .video {
                        Button {
                            call.toggleVideo()
                        } label: {
                            Label(call.cameraOff ? "Camera on" : "Camera off",
                                  systemImage: call.cameraOff ? "video.slash" : "video")
                        }
                    }
                }
                .font(.caption)
                .buttonStyle(.bordered)
                .disabled(call.engine == nil)

                Button(role: .destructive) {
                    finish()
                    dismiss()
                } label: {
                    Label("End call", systemImage: "phone.down.fill")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)

                Text("Experimental calling. System incoming calls require a signed, server-configured iOS release.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding(18)
            .navigationTitle("BLINK Call")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Close") {
                        finish()
                        dismiss()
                    }
                }
            }
        }
        .task {
            guard !started else { return }
            started = true
            switch start {
            case .outgoing(let conversation, let callee, let kind):
                await call.outgoing(conversationID: conversation, calleeID: callee, kind: kind)
            case .incoming(let id):
                await call.incoming(callID: id)
            case .systemAnswered:
                break // CallKit already verified/answered using this coordinator.
            }
        }
        .onChange(of: scenePhase) { _, phase in
            // Until PushKit/CallKit exist, stop the foreground-only media call
            // on backgrounding rather than pretending it remains reliably active.
            if phase == .background {
                if case .systemAnswered = start {
                    // CallKit manages system call lifecycle; do not create a
                    // second local hangup just because the UI backgrounds.
                } else {
                    Task { await call.end() }
                }
            }
        }
        .onDisappear {
            finish()
        }
    }
}

private struct BLINKRTCVideoRenderer: UIViewRepresentable {
    let track: RTCVideoTrack?

    func makeUIView(context: Context) -> RTCMTLVideoView {
        let view = RTCMTLVideoView(frame: .zero)
        view.videoContentMode = .scaleAspectFit
        view.backgroundColor = .black
        track?.add(view)
        return view
    }

    func updateUIView(_ view: RTCMTLVideoView, context: Context) {
        // A new view is used when the track object changes.
        track?.add(view)
    }

    static func dismantleUIView(_ view: RTCMTLVideoView, coordinator: ()) {}
}
