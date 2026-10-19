import Foundation
import Combine

/// Native feed state is isolated from Auth and shares BLINK's server ranking
/// contract. No local dummy posts or fake likes are created.
@MainActor
final class BLINKFeedStore: ObservableObject {
    @Published private(set) var posts: [BLINKFeedPost] = []
    @Published private(set) var authors: [String: BLINKFeedAuthor] = [:]
    @Published private(set) var loading = false
    @Published private(set) var loadingMore = false
    @Published private(set) var canLoadMore = false
    @Published private(set) var error: String?
    @Published private(set) var pendingLikes: Set<String> = []
    @Published private(set) var likedHere: Set<String> = []

    private let api: BLINKFeedAPI?
    private var offset = 0
    private var asOf: String?
    private var currentMode: BLINKFeedMode?
    private var generation = 0

    init(
        configuration: BLINKBackendConfiguration? = .fromBundle(),
        urlSession: URLSession = .shared
    ) {
        api = configuration.map {
            BLINKFeedAPI(configuration: $0, urlSession: urlSession)
        }
    }

    func load(mode: BLINKFeedMode, auth: BLINKAuthStore, force: Bool = false) async {
        if !force && currentMode == mode && !posts.isEmpty { return }
        generation += 1
        let version = generation
        currentMode = mode
        loading = true
        error = nil
        posts = []
        authors = [:]
        likedHere = []
        canLoadMore = false
        offset = 0
        asOf = nil
        defer { if version == generation { loading = false } }
        guard let api else {
            error = "BLINK is not configured to load the feed."
            return
        }
        do {
            let page = try await auth.withValidToken { token in
                try await api.feed(mode: mode, offset: 0, asOf: nil, accessToken: token)
            }
            guard version == generation else { return }
            apply(page: page, appending: false)
        } catch {
            guard version == generation else { return }
            self.error = error.localizedDescription
        }
    }

    func loadMore(auth: BLINKAuthStore) async {
        guard !loading, !loadingMore, canLoadMore, let mode = currentMode,
              let api else { return }
        loadingMore = true
        let version = generation
        defer { loadingMore = false }
        do {
            let cursor = offset
            let snapshot = asOf
            let page = try await auth.withValidToken { token in
                try await api.feed(mode: mode, offset: cursor, asOf: snapshot, accessToken: token)
            }
            guard version == generation else { return }
            apply(page: page, appending: true)
        } catch {
            guard version == generation else { return }
            self.error = error.localizedDescription
        }
    }

    func toggleLike(post: BLINKFeedPost, auth: BLINKAuthStore) async {
        guard !pendingLikes.contains(post.id), let api else { return }
        pendingLikes.insert(post.id)
        defer { pendingLikes.remove(post.id) }
        let target = !likedHere.contains(post.id)
        do {
            try await auth.withValidToken { token in
                try await api.like(postId: post.id, liked: target, accessToken: token)
            }
            if target { likedHere.insert(post.id) }
            else { likedHere.remove(post.id) }
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func apply(page: BLINKFeedPage, appending: Bool) {
        let existing = appending ? posts : []
        let ids = Set(existing.map(\.id))
        posts = existing + page.posts.filter { !ids.contains($0.id) }
        authors.merge(page.authors) { _, latest in latest }
        offset = max(offset, page.nextOffset)
        asOf = page.asOf
        canLoadMore = page.hasMore
        error = nil
    }
}
