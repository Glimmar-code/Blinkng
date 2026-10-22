import Foundation

/// The same server-owned conversation summary returned to BLINK Web.
/// Partner identifiers and unread counters are not derived from other users' data.
struct BLINKConversationSummary: Decodable, Identifiable {
    let conversationId: String
    let partnerId: String?
    let partnerUsername: String?
    let partnerName: String?
    let partnerAvatar: String?
    let partnerOnline: Bool?
    let lastMessage: String?
    let lastMessageAt: String?
    let unreadCount: Int?
    let cursorAt: String?

    var id: String { conversationId }
    var displayName: String {
        [partnerName, partnerUsername].compactMap { $0 }
            .first(where: { !$0.isEmpty }) ?? "Conversation"
    }
}

struct BLINKConversationMetadata: Decodable {
    let id: String
    let isGroup: Bool?
    let title: String?
    let avatarUrl: String?
}

struct BLINKChatMessage: Decodable, Identifiable {
    let id: String
    let conversationId: String?
    let senderId: String?
    let content: String?
    let mediaUrl: String?
    let deletedForEveryone: Bool?
    let createdAt: String?
    let editedAt: String?

    var displayedContent: String {
        deletedForEveryone == true ? "Message deleted" : (content ?? "")
    }

    var attachment: URL? {
        guard deletedForEveryone != true else { return nil }
        return BLINKMediaURL.safe(mediaUrl)
    }
}

struct BLINKMessageSent: Decodable {
    let messageId: String?
    let conversationId: String?
    let createdAt: String?
}

struct BLINKMessagesPage {
    let messages: [BLINKChatMessage]
    let beforeTime: String?
    let beforeId: String?
    let hasMore: Bool
}

enum BLINKMessagingError: LocalizedError {
    case unconfigured
    case invalidRecipient
    case invalidConversation
    case invalidText
    case unexpectedResponse
    case unavailable(String)

    var errorDescription: String? {
        switch self {
        case .unconfigured: "BLINK messaging is not configured."
        case .invalidRecipient: "Enter a valid BLINK username."
        case .invalidConversation: "Conversation is unavailable."
        case .invalidText: "Enter a message of up to 8,000 characters."
        case .unexpectedResponse: "BLINK returned an unexpected message response."
        case .unavailable(let reason): reason
        }
    }
}

/// Validation is deliberately local defense-in-depth. Server checks still
/// determine who may message, block, join groups or access conversations.
enum BLINKRecipient {
    static func normalize(_ raw: String) -> String? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let candidate = trimmed.first == "@" ? String(trimmed.dropFirst()) : trimmed
        guard !candidate.isEmpty, candidate.count <= 64,
              candidate.range(of: #"^[A-Za-z0-9_.]+$"#,
                              options: .regularExpression) != nil else { return nil }
        return candidate
    }

    static func content(_ raw: String) -> String? {
        let candidate = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !candidate.isEmpty, candidate.count <= 8000 else { return nil }
        return candidate
    }
}
