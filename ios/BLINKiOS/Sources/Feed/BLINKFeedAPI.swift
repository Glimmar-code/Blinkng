import Foundation

/// Real Supabase calls matching the existing BLINK web backend:
/// get_ranked_feed_page, get_following_feed, profiles, like_post,
/// unlike_post, get_post_comments and create_comment.
struct BLINKFeedAPI {
    let configuration: BLINKBackendConfiguration
    var urlSession: URLSession = .shared

    private let pageSize = 20

    func feed(mode: BLINKFeedMode, offset: Int, asOf: String?, accessToken: String) async throws -> BLINKFeedPage {
        let rows: [BLINKFeedEntry]
        if mode == .following {
            guard offset == 0 else {
                return BLINKFeedPage(posts: [], authors: [:], nextOffset: offset,
                                     asOf: nil, hasMore: false)
            }
            // The existing following RPC uses a cursor, not ranked offset.
            // Pagination for this mode is deferred until its cursor contract is ported.
            let data = try await rpc("get_following_feed",
                                     body: ["p_limit": pageSize, "p_cursor": NSNull()],
                                     token: accessToken)
            let raw = try decode([BLINKFeedPost].self, from: data)
            rows = raw.map { BLINKFeedEntry.direct($0) }
        } else {
            let data = try await rpc("get_ranked_feed_page", body: [
                "p_limit": pageSize,
                "p_offset": offset,
                "p_as_of": asOf as Any? ?? NSNull(),
                "p_feed_type": mode.rawValue
            ], token: accessToken)
            rows = try decode([BLINKFeedEntry].self, from: data)
        }
        let posts = rows.map(\.post)
        let authors = try await fetchAuthors(userIds: posts.map(\.userId), token: accessToken)
        let next = rows.first?.nextOffset ?? (offset + rows.count)
        return BLINKFeedPage(posts: posts, authors: authors,
                             nextOffset: next,
                             asOf: rows.first?.asOf ?? asOf,
                             hasMore: mode != .following && rows.count >= pageSize)
    }

    func like(postId: String, liked: Bool, accessToken: String) async throws {
        _ = try await rpc(liked ? "like_post" : "unlike_post",
                          body: ["p_post_id": postId], token: accessToken)
    }

    func comments(postId: String, accessToken: String) async throws -> [BLINKComment] {
        let data = try await rpc("get_post_comments", body: ["p_post_id": postId], token: accessToken)
        return try decode([BLINKComment].self, from: data)
    }

    func sendComment(postId: String, text: String, accessToken: String) async throws {
        _ = try await rpc("create_comment",
                          body: ["p_post_id": postId, "p_content": text,
                                 "p_parent_comment_id": NSNull()], token: accessToken)
    }

    private func fetchAuthors(userIds: [String], token: String) async throws -> [String: BLINKFeedAuthor] {
        let ids = Array(Set(userIds.filter { UUID(uuidString: $0) != nil })).sorted()
        if ids.isEmpty { return [:] }
        // Split large batches so a feed never produces an unbounded URL.
        var authors: [String: BLINKFeedAuthor] = [:]
        for start in stride(from: 0, to: ids.count, by: 25) {
            let group = ids[start..<min(start + 25, ids.count)]
            let data = try await request(
                path: "rest/v1/profiles", method: "GET",
                query: [
                    URLQueryItem(name: "select", value: "id,username,full_name,avatar_url,is_verified,verification_badge"),
                    URLQueryItem(name: "id", value: "in.(\(group.joined(separator: ",")))")
                ],
                body: nil, token: token
            )
            for author in try decode([BLINKFeedAuthor].self, from: data) {
                authors[author.id] = author
            }
        }
        return authors
    }

    private func rpc(_ name: String, body: [String: Any], token: String) async throws -> Data {
        try await request(path: "rest/v1/rpc/\(name)", method: "POST",
                          query: [], body: body, token: token)
    }

    private func decode<T: Decodable>(_ kind: T.Type, from data: Data) throws -> T {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        do { return try decoder.decode(kind, from: data) }
        catch { throw BLINKFeedError.malformedResponse }
    }

    private func request(path: String, method: String, query: [URLQueryItem],
                         body: [String: Any]?, token: String) async throws -> Data {
        var parts = URLComponents(
            url: configuration.url.appendingPathComponent(path),
            resolvingAgainstBaseURL: false
        )
        parts?.queryItems = query.isEmpty ? nil : query
        guard let url = parts?.url else { throw BLINKFeedError.malformedResponse }

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
            throw BLINKFeedError.malformedResponse
        }
        if http.statusCode == 401 { throw BLINKFeedError.unauthorized }
        guard (200..<300).contains(http.statusCode) else {
            let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            let message = (object?["message"] as? String)
                ?? (object?["error"] as? String)
                ?? "Could not load BLINK feed (HTTP \(http.statusCode))."
            throw BLINKFeedError.server(message)
        }
        return data
    }
}

// A following-feed row has no ranked wrapper metadata.
extension BLINKFeedEntry {
    static func direct(_ post: BLINKFeedPost) -> Self {
        // Encode an existing decoded model is not needed: provide a purpose-built
        // direct-entry initializer in this file's companion extension.
        Self(post: post)
    }
}
