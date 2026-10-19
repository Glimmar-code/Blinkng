import Foundation

/// Authenticated messaging over BLINK's existing Supabase Data API.
/// Messages, recipient rights, unread flags and group membership are
/// server-authoritative. No service keys are embedded in this client.
struct BLINKMessagesAPI {
    let configuration: BLINKBackendConfiguration
    var urlSession: URLSession = .shared
    private let limit = 50

    func conversations(before: String? = nil, beforeID: String? = nil,
                       token: String) async throws -> [BLINKConversationSummary] {
        let data = try await rpc("get_conversation_summaries_page", payload: [
            "p_limit": limit,
            "p_before": before as Any? ?? NSNull(),
            "p_before_id": beforeID as Any? ?? NSNull()
        ], token: token)
        return try decode([BLINKConversationSummary].self, from: data)
    }

    func metadata(conversationIds: [String], token: String) async throws -> [String: BLINKConversationMetadata] {
        let ids = Array(Set(conversationIds.filter { UUID(uuidString: $0) != nil })).sorted()
        guard !ids.isEmpty else { return [:] }
        var result: [String: BLINKConversationMetadata] = [:]
        for start in stride(from: 0, to: ids.count, by: 25) {
            let batch = ids[start..<min(start + 25, ids.count)]
            let data = try await request(
                path: "rest/v1/conversations", method: "GET",
                query: [
                    URLQueryItem(name: "id", value: "in.(\(batch.joined(separator: ",")))"),
                    URLQueryItem(name: "select", value: "id,is_group,title,avatar_url")
                ],
                body: nil, token: token
            )
            for info in try decode([BLINKConversationMetadata].self, from: data) {
                result[info.id] = info
            }
        }
        return result
    }

    func messages(conversationID: String, before: String? = nil,
                  beforeID: String? = nil, token: String) async throws -> BLINKMessagesPage {
        guard UUID(uuidString: conversationID) != nil else {
            throw BLINKMessagingError.invalidConversation
        }
        let data = try await rpc("get_conversation_messages_page", payload: [
            "p_conversation_id": conversationID,
            "p_limit": limit,
            "p_before": before as Any? ?? NSNull(),
            "p_before_id": beforeID as Any? ?? NSNull()
        ], token: token)
        let rows = try decode([BLINKChatMessage].self, from: data)
        // Backend sends newest first. UI needs oldest first.
        let oldest = rows.last
        return BLINKMessagesPage(
            messages: rows.reversed(),
            beforeTime: oldest?.createdAt,
            beforeId: oldest?.id,
            hasMore: rows.count >= limit
        )
    }

    func sendDirect(to rawUsername: String, content rawContent: String,
                    token: String) async throws -> BLINKMessageSent? {
        guard let username = BLINKRecipient.normalize(rawUsername) else {
            throw BLINKMessagingError.invalidRecipient
        }
        guard let content = BLINKRecipient.content(rawContent) else {
            throw BLINKMessagingError.invalidText
        }
        let data = try await rpc("send_message_v2", payload: [
            "p_receiver_username": username,
            "p_content": content
        ], token: token)
        let rows = try decode([BLINKMessageSent].self, from: data)
        return rows.first
    }

    func sendGroup(conversationID: String, content rawContent: String,
                   token: String) async throws {
        guard UUID(uuidString: conversationID) != nil else {
            throw BLINKMessagingError.invalidConversation
        }
        guard let content = BLINKRecipient.content(rawContent) else {
            throw BLINKMessagingError.invalidText
        }
        _ = try await rpc("send_group_message", payload: [
            "p_conversation_id": conversationID,
            "p_content": content
        ], token: token)
    }

    func markDirectRead(partner rawUsername: String, token: String) async throws {
        guard let username = BLINKRecipient.normalize(rawUsername) else {
            throw BLINKMessagingError.invalidRecipient
        }
        _ = try await rpc("mark_conversation_read",
                          payload: ["p_partner_username": username], token: token)
    }

    /// Updating a group receipt is scoped by both caller and conversation IDs.
    /// RLS must further enforce actual active group membership.
    func markGroupRead(conversationID: String, userID: String, token: String) async throws {
        guard UUID(uuidString: conversationID) != nil, UUID(uuidString: userID) != nil else {
            throw BLINKMessagingError.invalidConversation
        }
        _ = try await request(
            path: "rest/v1/conversation_participants", method: "PATCH",
            query: [
                URLQueryItem(name: "conversation_id", value: "eq.\(conversationID)"),
                URLQueryItem(name: "user_id", value: "eq.\(userID)")
            ],
            body: ["last_read_at": ISO8601DateFormatter().string(from: Date())],
            token: token
        )
    }

    private func rpc(_ function: String, payload: [String: Any],
                     token: String) async throws -> Data {
        try await request(path: "rest/v1/rpc/\(function)", method: "POST",
                          query: [], body: payload, token: token)
    }

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        do { return try decoder.decode(type, from: data) }
        catch { throw BLINKMessagingError.unexpectedResponse }
    }

    private func request(path: String, method: String,
                         query: [URLQueryItem] = [], body: [String: Any]?,
                         token: String) async throws -> Data {
        var parts = URLComponents(url: configuration.url.appendingPathComponent(path),
                                  resolvingAgainstBaseURL: false)
        parts?.queryItems = query.isEmpty ? nil : query
        guard let url = parts?.url else { throw BLINKMessagingError.unexpectedResponse }
        var request = URLRequest(url: url, timeoutInterval: 20)
        request.httpMethod = method
        request.setValue(configuration.publicKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let (data, response) = try await urlSession.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw BLINKMessagingError.unexpectedResponse
        }
        if http.statusCode == 401 { throw BLINKFeedError.unauthorized }
        guard (200..<300).contains(http.statusCode) else {
            let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            let message = (json?["message"] as? String)
                ?? (json?["error_description"] as? String)
                ?? (json?["error"] as? String)
                ?? "BLINK couldn't complete the message request (HTTP \(http.statusCode))."
            throw BLINKMessagingError.unavailable(message)
        }
        return data
    }
}
