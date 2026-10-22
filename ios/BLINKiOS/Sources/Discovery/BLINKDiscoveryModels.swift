import Foundation

/// Values come from the server's approved marketplace and Connect RPCs.
/// Client-side roles, badges, pricing and item status are never authoritative.
struct BLINKMarketplaceListing: Decodable, Identifiable {
    let id: String
    let title: String?
    let description: String?
    let condition: String?
    let location: String?
    let university: String?
    let category: String?
    let sellerUsername: String?
    let currency: String?
    let price: BLINKPrice?
    let imageUrls: [String]?
    let imageUrl: String?

    var coverImage: URL? {
        let first = imageUrls?.first ?? imageUrl
        return BLINKMediaURL.safe(first)
    }

    var sellerHandle: String? {
        guard let value = sellerUsername?.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"^@"#, with: "", options: .regularExpression),
              !value.isEmpty, value.count <= 40,
              value.range(of: #"^[A-Za-z0-9_.]+$"#, options: .regularExpression) != nil else {
            return nil
        }
        return value
    }

    var displayPrice: String {
        guard let price else { return "Ask seller for price" }
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = Locale(identifier: "en_NG")
        formatter.currencyCode = currency?.isEmpty == false ? currency : "NGN"
        return formatter.string(from: NSDecimalNumber(decimal: price.amount))
            ?? "Price unavailable"
    }
}

/// PostgreSQL numeric values can be serialized as JSON numbers or strings.
struct BLINKPrice: Decodable {
    let amount: Decimal

    init(from decoder: Decoder) throws {
        let value = try decoder.singleValueContainer()
        if let decimal = try? value.decode(Decimal.self) {
            amount = decimal
        } else if let string = try? value.decode(String.self),
                  let decimal = Decimal(string: string, locale: Locale(identifier: "en_US_POSIX")) {
            amount = decimal
        } else {
            throw DecodingError.dataCorruptedError(in: value, debugDescription: "Invalid price")
        }
    }
}

struct BLINKMarketplaceRow: Decodable {
    let item: BLINKMarketplaceListing

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let wrapped = try container.decodeIfPresent(BLINKMarketplaceListing.self, forKey: .item) {
            item = wrapped
        } else {
            item = try BLINKMarketplaceListing(from: decoder)
        }
    }

    private enum CodingKeys: String, CodingKey { case item }
}

enum BLINKConnectMode: String, CaseIterable, Identifiable {
    case all
    case roommate
    case mentor
    case readingMate = "reading_mate"
    case housing

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all: "All"
        case .roommate: "Roommates"
        case .mentor: "Mentors"
        case .readingMate: "Reading mates"
        case .housing: "Housing"
        }
    }
}

struct BLINKConnectProfile: Decodable {
    let id: String?
    let fullName: String?
    let username: String?
    let university: String?
    let department: String?
}

struct BLINKConnectPayload: Decodable {
    let title: String?
    let headline: String?
    let name: String?
    let description: String?
    let bio: String?
    let university: String?
    let department: String?
    let location: String?
    let preferredLocation: String?
    let profile: BLINKConnectProfile?
}

struct BLINKConnectOpportunity: Decodable {
    let kind: String?
    let payload: BLINKConnectPayload?

    var displayTitle: String {
        let options = [payload?.title, payload?.headline, payload?.profile?.fullName,
                       payload?.profile?.username, payload?.name]
        return options.compactMap { $0 }.first(where: { !$0.isEmpty })
            ?? "Connect opportunity"
    }

    var summary: String {
        payload?.description ?? payload?.bio ?? ""
    }

    var detail: String {
        let options = [payload?.profile?.university ?? payload?.university,
                       payload?.profile?.department ?? payload?.department,
                       payload?.location ?? payload?.preferredLocation]
        return options.compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
    }

    var receiverID: String? {
        guard let id = payload?.profile?.id, UUID(uuidString: id) != nil else { return nil }
        return id
    }
}

enum BLINKDiscoveryError: LocalizedError {
    case unexpectedResponse
    case unavailable
    case unauthenticated
    case server(String)

    var errorDescription: String? {
        switch self {
        case .unexpectedResponse: "BLINK received an unexpected marketplace response."
        case .unavailable: "BLINK discovery is temporarily unavailable."
        case .unauthenticated: "Please sign in again."
        case .server(let message): message
        }
    }
}
