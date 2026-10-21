import Foundation
import PushKit
import Combine

/// Apple only delivers VoIP pushes to properly entitled, signed devices.
/// Registration is gated until the separate APNs provider + secure Supabase
/// device registration service is deployed and the two-phone tests pass.
@MainActor
final class BLINKVoIPPushManager: NSObject, ObservableObject, PKPushRegistryDelegate {
    static let shared = BLINKVoIPPushManager()

    @Published private(set) var statusText =
        "Incoming iPhone calls need Apple signing and server setup."
    private var registry: PKPushRegistry?
    private var lastToken: Data?
    private var auth: BLINKAuthStore?
    private var userID: String?
    private var registeredForUser: String?
    private var registering = false

    private var deviceID: UUID {
        let key = "blink.ios.voip.deviceIdentifier.v1"
        if let raw = UserDefaults.standard.string(forKey: key),
           let value = UUID(uuidString: raw) { return value }
        let created = UUID()
        UserDefaults.standard.set(created.uuidString, forKey: key)
        return created
    }

    /// Must be called at application launch in a signed release-gated build.
    func startAtLaunchIfEnabled() {
        guard BLINKVoIPConfiguration.enabledInBuild else { return }
        guard registry == nil else { return }
        let voip = PKPushRegistry(queue: .main)
        voip.delegate = self
        voip.desiredPushTypes = [.voIP]
        registry = voip
        statusText = "Preparing secure iPhone call delivery…"
    }

    func bindUser(auth: BLINKAuthStore, userID: String) {
        guard UUID(uuidString: userID) != nil else { return }
        if self.userID != userID { registeredForUser = nil }
        self.auth = auth
        self.userID = userID
        if BLINKVoIPConfiguration.enabledInBuild {
            startAtLaunchIfEnabled()
            Task { await registerIfPossible() }
        }
    }

    /// Called by AuthStore before the JWT is invalidated on sign-out.
    /// The signed-in user may revoke only their own iOS device token.
    func revokeForSignOut(userJWT: String) async throws {
        guard BLINKVoIPConfiguration.enabledInBuild else { return }
        guard let configuration = BLINKBackendConfiguration.fromBundle() else {
            throw BLINKCallError.notAvailable
        }
        let environment = (Bundle.main.object(
            forInfoDictionaryKey: "BLINK_APNS_ENVIRONMENT"
        ) as? String) ?? ""
        try await BLINKVoIPRegistrationAPI(configuration: configuration)
            .revoke(deviceID: deviceID, environment: environment, userJWT: userJWT)
        registeredForUser = nil
    }

    /// Local state cleanup is independent from a successful server-side revoke.
    func clearUser() {
        auth = nil
        userID = nil
        registeredForUser = nil
        lastToken = nil
        registry?.desiredPushTypes = []
        registry = nil
        statusText = "BLINK iPhone calls are not enabled on this device."
    }

    func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials,
                      for type: PKPushType) {
        guard type == .voIP, !pushCredentials.token.isEmpty else { return }
        lastToken = pushCredentials.token
        registeredForUser = nil
        Task { await registerIfPossible() }
    }

    func pushRegistry(_ registry: PKPushRegistry, didInvalidatePushTokenFor type: PKPushType) {
        guard type == .voIP else { return }
        lastToken = nil
        registeredForUser = nil
        statusText = "Your iPhone call token has expired; new registration is needed."
    }

    func pushRegistry(_ registry: PKPushRegistry, didReceiveIncomingPushWith payload: PKPushPayload,
                      for type: PKPushType, completion: @escaping () -> Void) {
        guard type == .voIP else { completion(); return }
        // Apple's CallKit report must happen promptly, not after Supabase IO.
        // Only a trusted, secured BLINK APNs provider may send actual VoIP calls.
        guard let incoming = BLINKVoIPPushCall(dictionary: payload.dictionaryPayload) else {
            completion()
            return
        }
        BLINKCallKitManager.shared.reportIncoming(incoming) { _ in completion() }
    }

    private func registerIfPossible() async {
        guard BLINKVoIPConfiguration.enabledInBuild,
              !registering, let token = lastToken,
              let auth, let userID, registeredForUser != userID,
              let config = BLINKBackendConfiguration.fromBundle() else { return }
        let environment = (Bundle.main.object(
            forInfoDictionaryKey: "BLINK_APNS_ENVIRONMENT"
        ) as? String) ?? ""
        guard ["sandbox", "production"].contains(environment) else {
            statusText = "Apple push environment has not been configured."
            return
        }
        registering = true
        defer { registering = false }
        let api = BLINKVoIPRegistrationAPI(configuration: config)
        do {
            try await auth.withValidToken { jwt in
                try await api.register(token: token, deviceID: deviceID,
                                       environment: environment, userJWT: jwt)
            }
            guard self.userID == userID, lastToken == token else { return }
            registeredForUser = userID
            statusText = "iPhone VoIP token accepted by server; delivery still needs device verification."
        } catch {
            // Do not print APNs token/JWT, expose server error bodies, or fall
            // back to Android FCM registration.
            statusText = "iPhone call registration couldn't be completed."
        }
    }
}
