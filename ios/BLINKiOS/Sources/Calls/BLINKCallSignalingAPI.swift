import Foundation

/// Wire format matches BLINK Android CallRepository and existing Supabase RLS.
/// No client-owned call status writes or hard-coded TURN secrets.
struct BLINKCallSignal: Decodable, Identifiable {
    let id: Int64
    let callId: String
    let senderId: String
    let kind: String
    let payload: BLINKCallSignalPayload
    var createdAt: String?
}

struct BLINKCallSignalPayload: Codable {
    let sdp: String?
    let type: String?
    let candidate: String?
    let sdpMid: String?
    let sdpMLineIndex: Int?

    init(sdp: String? = nil, type: String? = nil, candidate: String? = nil,
         sdpMid: String? = nil, sdpMLineIndex: Int? = nil) {
        self.sdp = sdp
        self.type = type
        self.candidate = candidate
        self.sdpMid = sdpMid
        self.sdpMLineIndex = sdpMLineIndex
    }

    enum CodingKeys: String, CodingKey {
        case sdp, type, candidate, sdpMid, sdpMLineIndex
    }
}

struct BLINKIceServer: Decodable {
    let urls: [String]
    let username: String?
    let credential: String?

    enum CodingKeys: CodingKey { case urls, username, credential }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let list = try? container.decode([String].self, forKey: .urls) {
            urls = list
        } else if let single = try? container.decode(String.self, forKey: .urls) {
            urls = [single]
        } else {
            urls = []
        }
        username = try container.decodeIfPresent(String.self, forKey: .username)
        credential = try container.decodeIfPresent(String.self, forKey: .credential)
    }

    var safeURLs: [String] {
        urls.prefix(8).filter { value in
            let allowed = ["stun:", "stuns:", "turn:", "turns:"]
                .contains(where: { value.lowercased().hasPrefix($0) })
            return allowed && !value.contains(where: \.isWhitespace)
        }
    }

    var isAuthenticatedTURN: Bool {
        safeURLs.contains(where: {
            $0.hasPrefix("turn:") || $0.hasPrefix("turns:")
        }) && !(username ?? "").isEmpty && !(credential ?? "").isEmpty
    }
}

struct BLINKIceResponse: Decodable {
    let iceServers: [BLINKIceServer]?

    enum CodingKeys: String, CodingKey { case iceServers = "ice_servers" }
}

struct BLINKCallSignalingAPI {
    let configuration: BLINKBackendConfiguration
    var urlSession: URLSession = .shared

    func start(conversationID: String, calleeID: String, kind: BLINKCallKind,
               token: String) async throws -> BLINKCallRecord {
        guard valid(conversationID), valid(calleeID) else { throw BLINKCallError.invalidCallID }
        return try await rpcCall("start_call", body: [
            "p_conversation_id": conversationID,
            "p_callee_id": calleeID,
            "p_call_type": kind.rawValue
        ], token: token)
    }

    func answer(callID: String, token: String) async throws -> BLINKCallRecord {
        guard valid(callID) else { throw BLINKCallError.invalidCallID }
        return try await rpcCall("answer_call", body: ["p_call_id": callID], token: token)
    }

    func connected(callID: String, token: String) async throws {
        guard valid(callID) else { throw BLINKCallError.invalidCallID }
        _ = try await rpcCall("mark_call_connected", body: ["p_call_id": callID], token: token)
    }

    func call(callID: String, token: String) async throws -> BLINKCallRecord {
        guard valid(callID) else { throw BLINKCallError.invalidCallID }
        let data = try await request(
            path: "rest/v1/calls", method: "GET",
            query: [
                URLQueryItem(name: "id", value: "eq.\(callID)"),
                URLQueryItem(name: "select",
                             value: "id,conversation_id,caller_id,callee_id,call_type,status,created_at,timeout_at,ended_at")
            ],
            body: nil, token: token
        )
        let rows = try decode([BLINKCallRecord].self, data)
        guard let call = rows.first else { throw BLINKCallError.invalidCallID }
        return call
    }

    func signals(callID: String, after: Int64, token: String) async throws -> [BLINKCallSignal] {
        guard valid(callID), after >= 0 else { throw BLINKCallError.invalidCallID }
        let data = try await request(
            path: "rest/v1/call_signals", method: "GET",
            query: [
                URLQueryItem(name: "call_id", value: "eq.\(callID)"),
                URLQueryItem(name: "id", value: "gt.\(after)"),
                URLQueryItem(name: "select", value: "id,call_id,sender_id,kind,payload,created_at"),
                URLQueryItem(name: "order", value: "id.asc"),
                URLQueryItem(name: "limit", value: "200")
            ],
            body: nil, token: token
        )
        return try decode([BLINKCallSignal].self, data)
    }

    func signal(callID: String, senderID: String, kind: String,
                payload: BLINKCallSignalPayload, token: String) async throws {
        guard valid(callID), valid(senderID), ["offer","answer","ice"].contains(kind) else {
            throw BLINKCallError.invalidCallID
        }
        let encoded = try JSONEncoder().encode(payload)
        guard let dictionary = try JSONSerialization.jsonObject(with: encoded) as? [String: Any]
        else { throw BLINKCallError.unexpectedResponse }
        _ = try await request(
            path: "rest/v1/call_signals", method: "POST",
            query: [], body: [
                "call_id": callID, "sender_id": senderID,
                "kind": kind, "payload": dictionary
            ],
            token: token
        )
    }

    /// Fail closed when an authorized time-limited TURN relay isn't available.
    /// Unlike Android's compatibility fallback, iOS does NOT embed STUN-only
    /// or static production TURN secrets in the app.
    func relay(callID: String, token: String) async throws -> [BLINKIceServer] {
        guard valid(callID) else { throw BLINKCallError.invalidCallID }
        let data = try await request(
            path: "functions/v1/call-turn-credentials", method: "POST",
            query: [], body: ["call_id": callID], token: token
        )
        let decoder = JSONDecoder()
        let list: [BLINKIceServer]
        if let wrapped = try? decoder.decode(BLINKIceResponse.self, from: data),
           let servers = wrapped.iceServers {
            list = servers
        } else if let singular = try? decoder.decode(BLINKIceServer.self, from: data) {
            list = [singular]
        } else {
            throw BLINKCallError.notAvailable
        }
        let safe = Array(list.prefix(12).filter { !$0.safeURLs.isEmpty })
        guard safe.contains(where: \.isAuthenticatedTURN) else {
            throw BLINKCallError.notAvailable
        }
        return safe
    }

    /// The current edge endpoint targets Android push. It does not establish
    /// APNs/PushKit delivery to iPhones.
    func notifyAndroid(callID: String, token: String) async throws {
        guard valid(callID) else { throw BLINKCallError.invalidCallID }
        _ = try await request(
            path: "functions/v1/send-call-notification", method: "POST",
            query: [], body: ["call_id": callID, "event": "incoming"], token: token
        )
    }

    private func valid(_ uuid: String) -> Bool { UUID(uuidString: uuid) != nil }

    private func rpcCall(_ name: String, body: [String: Any], token: String) async throws -> BLINKCallRecord {
        let data = try await request(path: "rest/v1/rpc/\(name)", method: "POST",
                                     query: [], body: body, token: token)
        return try decode(BLINKCallRecord.self, data)
    }

    private func decode<T: Decodable>(_ type: T.Type, _ data: Data) throws -> T {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        do { return try decoder.decode(type, from: data) }
        catch { throw BLINKCallError.unexpectedResponse }
    }

    private func request(path: String, method: String, query: [URLQueryItem],
                         body: [String: Any]?, token: String) async throws -> Data {
        var components = URLComponents(url: configuration.url.appendingPathComponent(path),
                                       resolvingAgainstBaseURL: false)
        components?.queryItems = query.isEmpty ? nil : query
        guard let url = components?.url else { throw BLINKCallError.unexpectedResponse }
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
        guard let http = response as? HTTPURLResponse else { throw BLINKCallError.unexpectedResponse }
        if http.statusCode == 401 { throw BLINKFeedError.unauthorized }
        guard (200..<300).contains(http.statusCode) else {
            let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            throw BLINKCallError.server(
                (object?["message"] as? String) ??
                (object?["error"] as? String) ??
                "Call service unavailable (HTTP \(http.statusCode))."
            )
        }
        return data
    }
}
