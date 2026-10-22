import Foundation

struct BLINKNotificationAPI {
    let configuration: BLINKBackendConfiguration
    var urlSession: URLSession = .shared

    /// Existing production notifications table has SELECT + owner RLS,
    /// but no authenticated UPDATE grant. Do not claim to mark as read here.
    func list(token: String, limit: Int = 50) async throws -> [BLINKNotification] {
        var components = URLComponents(
            url: configuration.url.appendingPathComponent("rest/v1/notifications"),
            resolvingAgainstBaseURL: false
        )
        components?.queryItems = [
            URLQueryItem(name: "select",
                         value: "id,type,text,sub_text,created_at,is_read,target_type,target_id"),
            URLQueryItem(name: "order", value: "created_at.desc"),
            URLQueryItem(name: "limit", value: String(max(1, min(100, limit))))
        ]
        guard let url = components?.url else {
            throw BLINKNotificationError.unexpectedResponse
        }
        var request = URLRequest(url: url, timeoutInterval: 20)
        request.setValue(configuration.publicKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await urlSession.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw BLINKNotificationError.unexpectedResponse
        }
        if http.statusCode == 401 {
            throw BLINKFeedError.unauthorized
        }
        guard (200..<300).contains(http.statusCode) else {
            let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            throw BLINKNotificationError.server(
                object?["message"] as? String
                ?? "BLINK notifications are currently unavailable."
            )
        }
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        do { return try decoder.decode([BLINKNotification].self, from: data) }
        catch { throw BLINKNotificationError.unexpectedResponse }
    }
}
