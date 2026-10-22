import Foundation

struct BLINKAuthUser: Codable, Equatable, Identifiable {
    let id: String
    let email: String?

    enum CodingKeys: String, CodingKey {
        case id, email
    }
}

struct BLINKAuthResponse: Decodable {
    let accessToken: String?
    let refreshToken: String?
    let expiresIn: TimeInterval?
    let expiresAt: TimeInterval?
    let user: BLINKAuthUser?

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case expiresIn = "expires_in"
        case expiresAt = "expires_at"
        case user
    }
}

struct BLINKAuthSession: Codable, Equatable {
    let accessToken: String
    let refreshToken: String
    let expiresAt: Date

    init?(response: BLINKAuthResponse, now: Date = Date()) {
        guard let access = response.accessToken, !access.isEmpty,
              let refresh = response.refreshToken, !refresh.isEmpty else {
            return nil
        }
        accessToken = access
        refreshToken = refresh
        if let expiry = response.expiresAt, expiry > 0 {
            expiresAt = Date(timeIntervalSince1970: expiry)
        } else {
            expiresAt = now.addingTimeInterval(max(0, response.expiresIn ?? 3600))
        }
    }

    var shouldRefresh: Bool { expiresAt <= Date().addingTimeInterval(90) }
}

enum BLINKAuthError: LocalizedError {
    case notConfigured
    case malformedResponse
    case server(status: Int, message: String)

    var errorDescription: String? {
        switch self {
        case .notConfigured: "BLINK's secure connection is not configured."
        case .malformedResponse: "BLINK received an unexpected login response. Please try again."
        case .server(_, let message): message
        }
    }

    var invalidSession: Bool {
        if case .server(let status, _) = self {
            return status == 400 || status == 401 || status == 403
        }
        return false
    }
}
