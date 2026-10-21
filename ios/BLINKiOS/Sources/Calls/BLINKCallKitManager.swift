import Foundation
import AVFoundation
import CallKit
import Combine

/// CallKit integration behind an explicit build-time release gate.
/// The provider does not report any call unless a VoIP-enabled build has
/// real APNs server delivery and a received call payload.
@MainActor
final class BLINKCallKitManager: NSObject, ObservableObject, CXProviderDelegate {
    static let shared = BLINKCallKitManager()

    @Published private(set) var presentation: BLINKCallKitPresentation?
    @Published private(set) var activeSession: BLINKForegroundCallCoordinator?
    @Published private(set) var errorMessage: String?

    private let provider: CXProvider
    private let controller = CXCallController()
    private var reportedCalls: [UUID: BLINKVoIPPushCall] = [:]
    private var auth: BLINKAuthStore?
    private var userID: String?

    private override init() {
        let configuration = CXProviderConfiguration(localizedName: "BLINK")
        configuration.supportsVideo = true
        configuration.supportedHandleTypes = [.generic]
        configuration.maximumCallsPerCallGroup = 1
        configuration.maximumCallGroups = 1
        configuration.includesCallsInRecents = false
        provider = CXProvider(configuration: configuration)
        super.init()
        provider.setDelegate(self, queue: .main)
    }

    func setAccount(auth: BLINKAuthStore, userID: String) {
        guard UUID(uuidString: userID) != nil else { return }
        self.auth = auth
        self.userID = userID
        Task { await checkPreviouslyReportedCalls() }
    }

    func signOut() {
        auth = nil
        userID = nil
        // No previous user's incoming-call screen may remain exposed after sign-out.
        for callID in reportedCalls.keys {
            provider.reportCall(with: callID, endedAt: Date(), reason: .failed)
        }
        reportedCalls.removeAll()
        if let activeSession {
            Task { await activeSession.end() }
        }
        activeSession = nil
        presentation = nil
    }

    /// Must run immediately for every real VoIP call push (before network IO).
    /// The back-end MUST only send VoIP pushes for real, actionable calls.
    func reportIncoming(_ call: BLINKVoIPPushCall, completion: @escaping (Error?) -> Void) {
        guard BLINKVoIPConfiguration.enabledInBuild else {
            completion(BLINKCallError.notAvailable)
            return
        }
        guard reportedCalls[call.id] == nil else {
            completion(nil)
            return
        }
        reportedCalls[call.id] = call
        let update = CXCallUpdate()
        update.localizedCallerName = call.displayName
        update.remoteHandle = CXHandle(type: .generic, value: "BLINK caller")
        update.hasVideo = call.isVideo
        update.supportsHolding = false
        update.supportsGrouping = false
        update.supportsUngrouping = false
        update.supportsDTMF = false

        provider.reportNewIncomingCall(with: call.id, update: update) { [weak self] error in
            completion(error)
            Task { @MainActor [weak self] in
                if error != nil {
                    self?.reportedCalls.removeValue(forKey: call.id)
                } else {
                    await self?.checkReported(callID: call.id)
                }
            }
        }
    }

    /// After quickly reporting to CallKit, verify against BLINK's server
    /// using the authenticated user JWT before the user can actually answer.
    private func checkPreviouslyReportedCalls() async {
        for id in Array(reportedCalls.keys) {
            await checkReported(callID: id)
        }
    }

    private func checkReported(callID: UUID) async {
        guard reportedCalls[callID] != nil,
              let auth, let userID,
              let configuration = BLINKBackendConfiguration.fromBundle() else { return }
        let api = BLINKCallSignalingAPI(configuration: configuration)
        do {
            let call = try await auth.withValidToken { token in
                try await api.call(callID: callID.uuidString, token: token)
            }
            guard call.calleeId == userID, call.status == .ringing else {
                failReportedCall(callID)
                return
            }
        } catch {
            // Do not leave unverified incoming calls ringing indefinitely.
            failReportedCall(callID)
        }
    }

    private func failReportedCall(_ callID: UUID) {
        provider.reportCall(with: callID, endedAt: Date(), reason: .failed)
        reportedCalls.removeValue(forKey: callID)
    }

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        let callID = action.callUUID
        Task { @MainActor in
            guard reportedCalls[callID] != nil,
                  let auth, let userID else {
                action.fail()
                failReportedCall(callID)
                return
            }

            let candidate = BLINKForegroundCallCoordinator(auth: auth, userID: userID)
            await candidate.incoming(callID: callID.uuidString)
            // A ringing Supabase row alone is insufficient. The local media
            // peer must be initialized and the server answer RPC must succeed.
            guard candidate.record != nil, candidate.engine != nil else {
                action.fail()
                failReportedCall(callID)
                errorMessage = candidate.error ?? "Unable to answer this BLINK call."
                return
            }
            activeSession = candidate
            presentation = BLINKCallKitPresentation(id: callID)
            action.fulfill()
        }
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        let callID = action.callUUID
        Task { @MainActor in
            if let activeSession, activeSession.record?.id.lowercased() == callID.uuidString.lowercased() {
                await activeSession.end()
            } else if let auth, let configuration = BLINKBackendConfiguration.fromBundle() {
                let api = BLINKCallsAPI(configuration: configuration)
                do {
                    _ = try await auth.withValidToken { token in
                        try await api.decline(callID: callID.uuidString, token: token)
                    }
                } catch {
                    // A user may hang up even when offline. No false server
                    // acknowledgement or unbounded retries are claimed.
                }
            }
            activeSession = nil
            presentation = nil
            reportedCalls.removeValue(forKey: callID)
            action.fulfill()
        }
    }

    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        if let session = activeSession, session.muted != action.isMuted {
            session.toggleMute()
        }
        action.fulfill()
    }

    func providerDidReset(_ provider: CXProvider) {
        let session = activeSession
        activeSession = nil
        presentation = nil
        reportedCalls.removeAll()
        if let session { Task { await session.end() } }
    }

    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        // CallKit owns the system audio session once an answer is accepted.
        // Native WebRTC setup must not create a second independent call.
    }

    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {}

    func requestEnd(callID: UUID) {
        let action = CXEndCallAction(call: callID)
        controller.request(CXTransaction(action: action)) { [weak self] error in
            if let error {
                Task { @MainActor [weak self] in
                    self?.errorMessage = "Couldn't dismiss the iPhone call interface."
                }
                _ = error
            }
        }
    }

    func dismissPresentation() {
        presentation = nil
    }
}
