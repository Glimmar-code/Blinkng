import Foundation

/// iOS uses the same ranked Market/Connect RPCs as BLINK Web.
/// The backend decides listing approval, eligibility, visibility and RLS.
struct BLINKDiscoveryAPI {
    let configuration: BLINKBackendConfiguration
    var urlSession: URLSession = .shared

    func marketplace(query: String, category: String? = nil, token: String) async throws -> [BLINKMarketplaceListing] {
        // Match BLINK web's get_ranked_market_page initial-page contract.
        let payload: [String: Any] = [
            "p_query": query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? NSNull() : query.trimmingCharacters(in: .whitespacesAndNewlines),
            "p_category": category as Any? ?? NSNull(),
            "p_min_price": NSNull(), "p_max_price": NSNull(),
            "p_limit": 40,
            "p_cursor_score": NSNull(), "p_cursor_created_at": NSNull(),
            "p_cursor_id": NSNull(), "p_as_of": NSNull()
        ]
        let bytes = try await call(name: "get_ranked_market_page", payload: payload, token: token)
        return try decode([BLINKMarketplaceRow].self, from: bytes).map(\.item)
    }

    func connect(mode: BLINKConnectMode, token: String) async throws -> [BLINKConnectOpportunity] {
        let payload: [String: Any] = [
            "p_mode": mode.rawValue, "p_limit": 40,
            "p_cursor_score": NSNull(), "p_cursor_kind": NSNull(),
            "p_cursor_id": NSNull(), "p_as_of": NSNull()
        ]
        let bytes = try await call(name: "get_ranked_connect_opportunities", payload: payload, token: token)
        return try decode([BLINKConnectOpportunity].self, from: bytes)
    }

    /// A connection request is user-initiated and subject to server permissions.
    func requestConnection(receiverID: String, token: String) async throws {
        guard UUID(uuidString: receiverID) != nil else { throw BLINKDiscoveryError.unavailable }
        _ = try await call(
            name: "send_connection_request",
            payload: ["p_receiver_id": receiverID],
            token: token
        )
    }

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        do { return try decoder.decode(type, from: data) }
        catch { throw BLINKDiscoveryError.unexpectedResponse }
    }

    private func call(name: String, payload: [String: Any], token: String) async throws -> Data {
        let endpoint = configuration.url.appendingPathComponent("rest/v1/rpc/\(name)")
        var request = URLRequest(url: endpoint, timeoutInterval: 20)
        request.httpMethod = "POST"
        request.setValue(configuration.publicKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (bytes, response) = try await urlSession.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw BLINKDiscoveryError.unavailable
        }
        if http.statusCode == 401 { throw BLINKFeedError.unauthorized }
        guard (200..<300).contains(http.statusCode) else {
            let object = (try? JSONSerialization.jsonObject(with: bytes)) as? [String: Any]
            let message = (object?["message"] as? String)
                ?? (object?["error_description"] as? String)
                ?? (object?["error"] as? String)
                ?? "BLINK could not complete the request (HTTP \(http.statusCode))."
            throw BLINKDiscoveryError.server(message)
        }
        return bytes
    }
}
