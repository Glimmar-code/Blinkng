import Foundation

/// Authenticated call-state records provided by BLINK Supabase.
/// SDP/ICE media transport is not connected on iOS Phase 6.
enum BLINKCallKind: String, Decodable, CaseIterable, Identifiable {
    case audio
    case video

    var id: String { rawValue }
    var title: String { self == .audio ? "Voice" : "Video" }
}

enum BLINKCallStatus: String, Decodable {
    case ringing, connecting, connected, declined, busy, missed, cancelled, ended, failed

    var isTerminal: Bool {
        switch self {
        case .declined, .busy, .missed, .cancelled, .ended, .failed: return true
        case .ringing, .connecting, .connected: return false
        }
    }
}

struct BLINKCallRecord: Decodable, Identifiable {
    let id: String
    let conversationId: String
    let callerId: String
    let calleeId: String
    let callType: BLINKCallKind
    let status: BLINKCallStatus
    let createdAt: String?
    let timeoutAt: String?
    let endedAt: String?

    var validID: Bool { UUID(uuidString: id) != nil }
    func isIncoming(for userID: String) -> Bool { calleeId == userID }
    func isOutgoing(for userID: String) -> Bool { callerId == userID }

    var canDecline: Bool { status == .ringing }
    var canEnd: Bool { !status.isTerminal }
}

/// Required safeguards before permitting live voice or video media calls.
/// This feature gate is intentionally false until tested WebRTC media/ICE,
/// server push, CallKit, and TURN are in place.
enum BLINKiOSCallAvailability {
    static let mediaTransportReady = false
    static let incomingVoIPPushReady = false
    static let systemCallKitReady = false
}

enum BLINKCallError: LocalizedError {
    case invalidCallID
    case unexpectedResponse
    case notAvailable
    case server(String)

    var errorDescription: String? {
        switch self {
        case .invalidCallID: "The call identifier is invalid."
        case .unexpectedResponse: "BLINK could not read the call response."
        case .notAvailable: "BLINK voice and video calling is not enabled on iPhone yet."
        case .server(let reason): reason
        }
    }
}
