import Foundation

/// Client contract for a FUTURE secure Supabase Edge Function. No direct DB
/// writes: the function must verify auth.uid(), device ownership, app bundle,
/// signing environment, token revocation and per-user rate limits.
struct BLINKVoIPRegistrationAPI {
    let configuration: BLINKBackendConfiguration
    var urlSession: URLSession = .shared

    func register(token: Data, deviceID: UUID, environment: String,
                  userJWT: String) async throws {
        guard BLINKVoIPConfiguration.enabledInBuild else {
            throw BLINKCallError.notAvailable
        }
        guard !token.isEmpty,
              token.count <= 256,
              ["sandbox", "production"].contains(environment) else {
            throw BLINKCallError.notAvailable
        }
        let url = configuration.url.appendingPathComponent(
            BLINKVoIPConfiguration.registrationPath
        )
        var request = URLRequest(url: url, timeoutInterval: 20)
        request.httpMethod = "POST"
        request.setValue(configuration.publicKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(userJWT)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "platform": "ios", "channel": "voip",
            "bundle_id": Bundle.main.bundleIdentifier ?? "",
            "device_id": deviceID.uuidString,
            "environment": environment,
            "token": token.map { String(format: "%02x", $0) }.joined()
        ])
        let (_, response) = try await urlSession.data(for: request)
        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode) else {
            throw BLINKCallError.notAvailable
        }
    }
}
