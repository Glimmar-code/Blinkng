import Foundation

/// A notification returned by Supabase under the current user's RLS policy.
/// Never fabricate notifications from failed requests.
struct BLINKNotification: Decodable, Identifiable {
    let id: String
    let type: String?
    let text: String?
    let subText: String?
    let createdAt: String?
    let isRead: Bool?
    let targetType: String?
    let targetId: String?

    var title: String {
        guard let value = text?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty else { return "BLINK notification" }
        return value
    }

    var detail: String? {
        guard let value = subText?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty else { return nil }
        return value
    }
}

enum BLINKNotificationError: LocalizedError {
    case notConfigured
    case unauthorized
    case unexpectedResponse
    case server(String)

    var errorDescription: String? {
        switch self {
        case .notConfigured: "BLINK notifications are not configured."
        case .unauthorized: "Your BLINK session expired. Please sign in again."
        case .unexpectedResponse: "BLINK could not read the notification response."
        case .server(let reason): reason
        }
    }
}

/// APNs tokens must be scoped to the specific application and environment.
/// They are not interchangeable with Android FCM tokens.
struct BLINKAPNsRegistrationState: Equatable {
    enum Status: Equatable {
        case notRequested
        case authorized
        case denied
        case failed
        case tokenCaptured
    }
    var status: Status = .notRequested
    var message: String = "Push notification delivery isn't configured for this iOS preview."
}
