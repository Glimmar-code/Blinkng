import Foundation

/// Postgres feed rows and author profiles come from the existing BLINK backend.
/// No fabricated content is returned if Supabase is unavailable.
struct BLINKFeedPost: Decodable, Identifiable {
    let id: String
    let userId: String
    let text: String?
    let caption: String?
    let createdAt: String?
    let images: [String]?
    let imageUrl: String?
    let videoUrl: String?
    let isReel: Bool?
    let isSponsored: Bool?
    let likeCount: Int?
    let commentCount: Int?
    let viewCount: Int?
    let repostCount: Int?
    let altText: String?

    var postText: String { (text?.isEmpty == false ? text : caption) ?? "" }
    var mediaImages: [URL] {
        let candidates = (images?.isEmpty == false ? images! : [imageUrl].compactMap { $0 })
        return candidates.compactMap(BLINKMediaURL.safe).prefix(4).map { $0 }
    }
    var mediaVideo: URL? { BLINKMediaURL.safe(videoUrl) }
}

enum BLINKMediaURL {
    static func safe(_ raw: String?) -> URL? {
        guard let raw, let parts = URLComponents(string: raw),
              parts.scheme?.lowercased() == "https", parts.host != nil,
              parts.user == nil, parts.password == nil else { return nil }
        return parts.url
    }
}

struct BLINKFeedAuthor: Decodable {
    let id: String
    let username: String?
    let fullName: String?
    let avatarUrl: String?
    let isVerified: Bool?
    let verificationBadge: String?

    var displayName: String {
        let name = fullName?.trimmingCharacters(in: .whitespacesAndNewlines)
        return name?.isEmpty == false ? name! : (username ?? "BLINK user")
    }
}

struct BLINKFeedEntry: Decodable {
    let post: BLINKFeedPost
    let nextOffset: Int?
    let asOf: String?

    enum CodingKeys: String, CodingKey { case item, nextOffset, asOf }

    init(from decoder: Decoder) throws {
        let wrapper = try decoder.container(keyedBy: CodingKeys.self)
        nextOffset = try wrapper.decodeIfPresent(Int.self, forKey: .nextOffset)
        asOf = try wrapper.decodeIfPresent(String.self, forKey: .asOf)
        if let nested = try wrapper.decodeIfPresent(BLINKFeedPost.self, forKey: .item) {
            post = nested
        } else {
            post = try BLINKFeedPost(from: decoder)
        }
    }
}

struct BLINKFeedPage {
    let posts: [BLINKFeedPost]
    let authors: [String: BLINKFeedAuthor]
    let nextOffset: Int
    let asOf: String?
    let hasMore: Bool
}

struct BLINKComment: Decodable, Identifiable {
    let id: String
    let username: String?
    let displayName: String?
    let content: String
    let createdAt: String?
}

enum BLINKFeedMode: String, CaseIterable, Hashable {
    case posts
    case following
    case reels

    var title: String {
        switch self {
        case .posts: "For You"
        case .following: "Following"
        case .reels: "Reels"
        }
    }
}

enum BLINKFeedError: LocalizedError {
    case malformedResponse
    case notSignedIn
    case unauthorized
    case server(String)

    var errorDescription: String? {
        switch self {
        case .malformedResponse: "BLINK received unexpected feed data."
        case .notSignedIn: "Please sign in to use your BLINK feed."
        case .unauthorized: "Your session expired. Sign in again."
        case .server(let message): message
        }
    }
}
