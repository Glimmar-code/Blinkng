import Foundation

/// Minimal GoTrue REST transport using BLINK's existing Supabase project.
/// Publishable keys identify the application; user access JWTs authenticate users.
/// No elevated service-role credentials, fake accounts, or payment operations.
struct BLINKAuthAPI {
    let configuration: BLINKBackendConfiguration
    var urlSession: URLSession = .shared

    func signIn(email: String, password: String) async throws -> (BLINKAuthSession, BLINKAuthUser) {
        let data = try await request(
            path: "auth/v1/token", method: "POST",
            body: ["email": email, "password": password],
            query: [URLQueryItem(name: "grant_type", value: "password")]
        )
        let response = try decode(BLINKAuthResponse.self, from: data)
        guard let session = BLINKAuthSession(response: response) else {
            throw BLINKAuthError.malformedResponse
        }
        let user: BLINKAuthUser
        if let confirmed = response.user {
            user = confirmed
        } else {
            user = try await currentUser(accessToken: session.accessToken)
        }
        return (session, user)
    }

    /// With email confirmation enabled Supabase returns a user but no session.
    func signUp(email: String, password: String) async throws -> (BLINKAuthSession?, BLINKAuthUser?) {
        let data = try await request(path: "auth/v1/signup", method: "POST",
                                     body: ["email": email, "password": password])
        let response = try decode(BLINKAuthResponse.self, from: data)
        return (BLINKAuthSession(response: response), response.user)
    }

    func refresh(refreshToken: String) async throws -> BLINKAuthSession {
        let data = try await request(
            path: "auth/v1/token", method: "POST",
            body: ["refresh_token": refreshToken],
            query: [URLQueryItem(name: "grant_type", value: "refresh_token")]
        )
        let response = try decode(BLINKAuthResponse.self, from: data)
        guard let session = BLINKAuthSession(response: response) else {
            throw BLINKAuthError.malformedResponse
        }
        return session
    }

    func currentUser(accessToken: String) async throws -> BLINKAuthUser {
        let data = try await request(path: "auth/v1/user", bearer: accessToken)
        return try decode(BLINKAuthUser.self, from: data)
    }

    /// Clears this device's Supabase Auth session, not other signed-in devices.
    func signOut(accessToken: String) async throws {
        _ = try await request(
            path: "auth/v1/logout", method: "POST",
            bearer: accessToken,
            query: [URLQueryItem(name: "scope", value: "local")]
        )
    }

    /// Supabase uses its configured SITE_URL; an iOS reset callback is not yet enabled.
    func requestPasswordReset(email: String) async throws {
        _ = try await request(path: "auth/v1/recover", method: "POST",
                              body: ["email": email])
    }

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        do { return try JSONDecoder().decode(type, from: data) }
        catch { throw BLINKAuthError.malformedResponse }
    }

    private func request(
        path: String,
        method: String = "GET",
        body: [String: String]? = nil,
        bearer: String? = nil,
        query: [URLQueryItem] = []
    ) async throws -> Data {
        var urlParts = URLComponents(
            url: configuration.url.appendingPathComponent(path), resolvingAgainstBaseURL: false
        )
        urlParts?.queryItems = query.isEmpty ? nil : query
        guard let url = urlParts?.url else { throw BLINKAuthError.malformedResponse }

        var request = URLRequest(url: url, timeoutInterval: 20)
        request.httpMethod = method
        request.setValue(configuration.publicKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        // sb_publishable keys are not JWTs. Do not use them as Bearer tokens.
        if let bearer {
            request.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }

        let (data, response) = try await urlSession.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw BLINKAuthError.malformedResponse
        }
        guard (200..<300).contains(http.statusCode) else {
            let responseBody = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            let reason = (responseBody?["msg"] as? String)
                ?? (responseBody?["error_description"] as? String)
                ?? (responseBody?["message"] as? String)
                ?? (responseBody?["error"] as? String)
                ?? "Request failed (HTTP \(http.statusCode))."
            throw BLINKAuthError.server(status: http.statusCode, message: reason)
        }
        return data
    }
}
