import SwiftUI
import AVKit

/// Home and Reels both read real BLINK records from Supabase.
/// Restricted/blocked content remains subject to the same server RLS rules.
struct BLINKFeedView: View {
    let initialMode: BLINKFeedMode
    @ObservedObject var auth: BLINKAuthStore
    @StateObject private var feed = BLINKFeedStore()
    @State private var mode: BLINKFeedMode
    @State private var commentPost: BLINKFeedPost?

    init(mode: BLINKFeedMode, auth: BLINKAuthStore) {
        initialMode = mode
        self.auth = auth
        _mode = State(initialValue: mode)
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                HStack {
                    Text(initialMode == .reels ? "Reels" : "Home")
                        .font(.title2.bold())
                    Spacer()
                    Button {
                        Task { await feed.load(mode: mode, auth: auth, force: true) }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .accessibilityLabel("Refresh feed")
                    }
                    .disabled(feed.loading)
                }
                .padding(.horizontal, 18)

                if initialMode != .reels {
                    Picker("Feed", selection: $mode) {
                        Text("For You").tag(BLINKFeedMode.posts)
                        Text("Following").tag(BLINKFeedMode.following)
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, 18)
                }

                if feed.loading {
                    ProgressView("Loading BLINK posts…")
                        .frame(maxWidth: .infinity, minHeight: 190)
                } else if let error = feed.error, feed.posts.isEmpty {
                    ContentUnavailableView {
                        Label("Feed unavailable", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text(error)
                    } actions: {
                        Button("Try again") {
                            Task { await feed.load(mode: mode, auth: auth, force: true) }
                        }
                        .buttonStyle(.borderedProminent)
                    }
                } else if feed.posts.isEmpty {
                    ContentUnavailableView {
                        Label("No posts yet", systemImage: "text.bubble")
                    } description: {
                        Text(mode == .following
                            ? "Follow people on BLINK to see their posts here."
                            : "No BLINK posts are available for your account right now.")
                    }
                }

                ForEach(feed.posts) { post in
                    BLINKPostCard(
                        post: post,
                        author: feed.authors[post.userId],
                        locallyLiked: feed.likedHere.contains(post.id),
                        likePending: feed.pendingLikes.contains(post.id),
                        onLike: { Task { await feed.toggleLike(post: post, auth: auth) } },
                        onComments: { commentPost = post }
                    )
                    .padding(.horizontal, 12)
                }

                if feed.canLoadMore {
                    Button {
                        Task { await feed.loadMore(auth: auth) }
                    } label: {
                        if feed.loadingMore {
                            ProgressView()
                        } else {
                            Text("Load more")
                        }
                    }
                    .disabled(feed.loadingMore)
                    .buttonStyle(.bordered)
                    .padding(12)
                }

                if let error = feed.error, !feed.posts.isEmpty {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding()
                }
            }
            .padding(.vertical, 12)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .refreshable { await feed.load(mode: mode, auth: auth, force: true) }
        .task(id: mode) { await feed.load(mode: mode, auth: auth, force: true) }
        .sheet(item: $commentPost) { post in
            NavigationStack {
                BLINKCommentsView(post: post, auth: auth)
            }
        }
    }
}

private struct BLINKPostCard: View {
    let post: BLINKFeedPost
    let author: BLINKFeedAuthor?
    let locallyLiked: Bool
    let likePending: Bool
    let onLike: () -> Void
    let onComments: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                if let avatar = BLINKMediaURL.safe(author?.avatarUrl) {
                    AsyncImage(url: avatar) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        Image(systemName: "person.crop.circle.fill")
                            .resizable().scaledToFit()
                    }
                    .frame(width: 42, height: 42)
                    .clipShape(Circle())
                    .accessibilityHidden(true)
                } else {
                    Image(systemName: "person.crop.circle.fill")
                        .font(.system(size: 38))
                        .foregroundStyle(.secondary)
                        .accessibilityHidden(true)
                }
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 4) {
                        Text(author?.displayName ?? "BLINK user")
                            .font(.subheadline.bold())
                        if author?.isVerified == true {
                            Image(systemName: "checkmark.seal.fill")
                                .foregroundStyle(.blue)
                                .accessibilityLabel("Verified account")
                        }
                    }
                    Text("@\(author?.username ?? "user")")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if post.isSponsored == true {
                    Text("Sponsored")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            if !post.postText.isEmpty {
                Text(post.postText)
                    .font(.body)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let video = post.mediaVideo {
                BLINKVideoView(url: video)
                    .frame(height: 280)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            } else if !post.mediaImages.isEmpty {
                TabView {
                    ForEach(post.mediaImages, id: \.self) { url in
                        AsyncImage(url: url) { image in
                            image.resizable().scaledToFit()
                        } placeholder: {
                            ProgressView()
                        }
                        .frame(maxWidth: .infinity)
                        .accessibilityLabel(post.altText ?? "BLINK post image")
                    }
                }
                .tabViewStyle(.page)
                .frame(height: 280)
                .background(Color(uiColor: .secondarySystemGroupedBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            HStack(spacing: 14) {
                Text("\(post.likeCount ?? 0) likes")
                Text("\(post.commentCount ?? 0) comments")
                Spacer(minLength: 0)
                Text("\(post.viewCount ?? 0) views")
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            HStack {
                Button(action: onLike) {
                    Label(locallyLiked ? "Liked" : "Like",
                          systemImage: locallyLiked ? "heart.fill" : "heart")
                }
                .disabled(likePending)
                Button(action: onComments) {
                    Label("Comment", systemImage: "bubble.left")
                }
                Spacer()
                if UUID(uuidString: post.id) != nil,
                   let shareURL = URL(string: "https://www.blink.com.ng/\(post.isReel == true ? "reel" : "post")/\(post.id)") {
                    ShareLink(item: shareURL) {
                        Label("Share", systemImage: "square.and.arrow.up")
                    }
                }
            }
            .font(.subheadline)
            .buttonStyle(.plain)
            .padding(.top, 4)
        }
        .padding(14)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16)
            .stroke(Color.primary.opacity(0.08), lineWidth: 1))
    }
}

private struct BLINKVideoView: View {
    @State private var player: AVPlayer

    init(url: URL) {
        _player = State(initialValue: AVPlayer(url: url))
    }

    var body: some View {
        VideoPlayer(player: player)
            .onDisappear { player.pause() }
            .accessibilityLabel("BLINK video")
    }
}

private struct BLINKCommentsView: View {
    let post: BLINKFeedPost
    @ObservedObject var auth: BLINKAuthStore

    @State private var comments: [BLINKComment] = []
    @State private var draft = ""
    @State private var loading = true
    @State private var submitting = false
    @State private var error: String?
    @Environment(\.dismiss) private var dismiss

    private var api: BLINKFeedAPI? {
        BLINKBackendConfiguration.fromBundle().map { BLINKFeedAPI(configuration: $0) }
    }

    var body: some View {
        VStack(spacing: 10) {
            if loading {
                ProgressView("Loading comments…")
                Spacer()
            } else if let error, comments.isEmpty {
                Text(error).foregroundStyle(.secondary)
                Button("Retry") { Task { await reload() } }
                Spacer()
            } else {
                List {
                    ForEach(comments) { comment in
                        VStack(alignment: .leading, spacing: 5) {
                            Text(comment.displayName ?? comment.username ?? "BLINK user")
                                .font(.subheadline.bold())
                            Text(comment.content)
                                .font(.body)
                        }
                        .padding(.vertical, 4)
                    }
                }
                .listStyle(.plain)
                if comments.isEmpty {
                    Text("No comments yet.")
                        .foregroundStyle(.secondary)
                }
            }

            HStack {
                TextField("Write a comment…", text: $draft, axis: .vertical)
                    .lineLimit(1...4)
                    .textFieldStyle(.roundedBorder)
                    .accessibilityLabel("New comment")
                Button("Send") { Task { await send() } }
                    .disabled(submitting || draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                              || draft.count > 2000)
            }
            .padding(12)
        }
        .navigationTitle("Comments")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }
            }
        }
        .task { await reload() }
    }

    private func reload() async {
        guard let api else { error = "BLINK connection is unavailable."; loading = false; return }
        loading = true
        defer { loading = false }
        do {
            comments = try await auth.withValidToken { token in
                try await api.comments(postId: post.id, accessToken: token)
            }
            error = nil
        } catch { self.error = error.localizedDescription }
    }

    private func send() async {
        let content = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !submitting, !content.isEmpty, content.count <= 2000, let api else { return }
        submitting = true
        defer { submitting = false }
        do {
            try await auth.withValidToken { token in
                try await api.sendComment(postId: post.id, text: content, accessToken: token)
            }
            draft = ""
            await reload()
        } catch { self.error = error.localizedDescription }
    }
}
