import Foundation

/// Phase 6: server call-state observation and safe call cleanup.
/// Starting/answering new calls stays gated until WebRTC and native
/// CallKit/PushKit integration actually transmit real media.
struct BLINKCallsAPI {
    let configuration: BLINKBackendConfiguration
    var urlSession: URLSession = .shared

    func recent(token: String, limit: Int = 35) async throws -> [BLINKCallRecord] {
        var components = URLComponents(
            url: configuration.url.appendingPathComponent("rest/v1/calls"),
            resolvingAgainstBaseURL: false
        )
        components?.queryItems = [
            URLQueryItem(name: "select",
                         value: "id,conversation_id,caller_id,callee_id,call_type,status,created_at,timeout_at,ended_at"),
            URLQueryItem(name: "order", value: "created_at.desc"),
            URLQueryItem(name: "limit", value: String(max(1, min(100, limit))))
        ]
        guard let url = components?.url else { throw BLINKCallError.unexpectedResponse }
        let data = try await perform(url: url, method: "GET", payload: nil, token: token)
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        do { return try decoder.decode([BLINKCallRecord].self, from: data) }
        catch { throw BLINKCallError.unexpectedResponse }
    }

    func decline(callID: String, token: String) async throws -> BLINKCallRecord {
        try await rpc("decline_call", callID: callID, reason: nil, token: token)
    }

    func end(callID: String, token: String) async throws -> BLINKCallRecord {
        try await rpc("end_call", callID: callID, reason: "ios_unavailable", token: token)
    }

    func expire(callID: String, token: String) async throws -> BLINKCallRecord {
        try await rpc("expire_call", callID: callID, reason: nil, token: token)
    }

    private func rpc(_ name: String, callID: String, reason: String?,
                     token: String) async throws -> BLINKCallRecord {
        guard UUID(uuidString: callID) != nil else { throw BLINKCallError.invalidCallID }
        var payload: [String: Any] = ["p_call_id": callID]
        if let reason { payload["p_reason"] = reason }
        let url = configuration.url.appendingPathComponent("rest/v1/rpc/\(name)")
        let data = try await perform(url: url, method: "POST", payload: payload, token: token)
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        do { return try decoder.decode(BLINKCallRecord.self, from: data) }
        catch { throw BLINKCallError.unexpectedResponse }
    }

    private func perform(url: URL, method: String,
                         payload: [String: Any]?, token: String) async throws -> Data {
        var request = URLRequest(url: url, timeoutInterval: 20)
        request.httpMethod = method
        request.setValue(configuration.publicKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let payload {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        }
        let (data, response) = try await urlSession.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw BLINKCallError.unexpectedResponse
        }
        if http.statusCode == 401 { throw BLINKFeedError.unauthorized }
        guard (200..<300).contains(http.statusCode) else {
            let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            throw BLINKCallError.server(
                (json?["message"] as? String)
                    ?? (json?["error"] as? String)
                    ?? "Call request failed (HTTP \(http.statusCode))."
            )
        }
        return data
    }
}
