import Foundation

/// Keep route names and tab order aligned with the existing Android UI.
enum BLINKSection: String, CaseIterable, Identifiable, Hashable {
    case home
    case reels
    case market
    case connect
    case message

    var id: Self { self }

    var title: String {
        switch self {
        case .home: "Home"
        case .reels: "Reels"
        case .market: "Market"
        case .connect: "Connect"
        case .message: "Message"
        }
    }

    var symbol: String {
        switch self {
        case .home: "house"
        case .reels: "play.rectangle"
        case .market: "bag"
        case .connect: "person.2"
        case .message: "bubble.left.and.bubble.right"
        }
    }

    var description: String {
        switch self {
        case .home: "Posts from BLINK will appear here after account and feed integration."
        case .reels: "Real BLINK reels will appear here after video and feed integration."
        case .market: "Verified BLINK marketplace listings will appear here after backend integration."
        case .connect: "Your BLINK campus connections will appear here after account integration."
        case .message: "Your real BLINK conversations will appear here after secure messaging integration."
        }
    }
}
