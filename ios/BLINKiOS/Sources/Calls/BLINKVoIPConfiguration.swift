import Foundation

/// Rollout safety: creating PushKit registry without working server delivery
/// and a real call engine can cause iOS to terminate the app. The native VoIP
/// implementation must stay opt-in through a *signed build configuration*.
/// Apple Developer APNs, VoIP token endpoint and real-device tests are not
/// provisioned in the repository as of Phase 8.
enum BLINKVoIPConfiguration {
    static func enabled(values: [String: Any]) -> Bool {
        (values["BLINK_VOIP_PUSH_ENABLED"] as? String)?.uppercased() == "YES"
    }

    static var enabledInBuild: Bool {
        enabled(values: Bundle.main.infoDictionary ?? [:])
    }

    /// Token registration endpoint is deliberately separate from Android FCM.
    static let registrationPath = "functions/v1/ios-voip-register"

    static let maximumCallerNameLength = 64
}

/// A VoIP push is a *request to verify a server-side call*, never proof that a
/// call is real, permitted or active. Do not trust caller labels from payload.
struct BLINKVoIPPushCall: Identifiable, Equatable {
    let id: UUID
    let displayName: String
    let isVideo: Bool

    init?(dictionary: [AnyHashable: Any]) {
        guard let raw = dictionary["call_id"] as? String,
              let uuid = UUID(uuidString: raw) else { return nil }
        id = uuid
        // Display only the trusted BLINK app label until an authenticated
        // Supabase lookup verifies the caller and call membership.
        displayName = "BLINK caller"
        isVideo = dictionary["call_type"] as? String == "video"
    }
}

struct BLINKCallKitPresentation: Identifiable {
    let id: UUID
}
