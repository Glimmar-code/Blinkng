import SwiftUI

/// Native iOS inbox using the same conversations and user identities as Android/Web.
/// Messages are kept only in in-memory view state and never written to analytics.
struct BLINKMessagesView: View {
    @ObservedObject var auth: BLINKAuthStore
    let userID: String
    @Binding var draftRecipient: String?

    @State private var summaries: [BLINKConversationSummary] = []
    @State private var metadata: [String: BLINKConversationMetadata] = [:]
    @State private var loading = false
    @State private var error: String?
    @State private var showComposer = false
    @State private var newRecipient = ""

    private var api: BLINKMessagesAPI? {
        BLINKBackendConfiguration.fromBundle().map { BLINKMessagesAPI(configuration: $0) }
    }

    var body: some View {
        Group {
            if loading && summaries.isEmpty {
                ProgressView("Loading conversations…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error, summaries.isEmpty {
                ContentUnavailableView {
                    Label("Messages unavailable", systemImage: "wifi.exclamationmark")
                } description: {
                    Text(error)
                } actions: {
                    Button("Retry") { Task { await reload() } }
                        .buttonStyle(.borderedProminent)
                }
            } else if summaries.isEmpty {
                ContentUnavailableView {
                    Label("No conversations yet", systemImage: "bubble.left.and.bubble.right")
                } description: {
                    Text("Start a conversation with someone on BLINK.")
                } actions: {
                    Button("New message") { newRecipient = ""; showComposer = true }
                        .buttonStyle(.borderedProminent)
                }
            } else {
                List {
                    ForEach(summaries) { conversation in
                        let group = metadata[conversation.conversationId]
                        NavigationLink {
                            BLINKConversationView(
                                auth: auth,
                                userID: userID,
                                summary: conversation,
                                group: group
                            )
                        } label: {
                            BLINKConversationRow(summary: conversation, group: group)
                        }
                    }
                }
                .listStyle(.plain)
                .refreshable { await reload() }
            }
        }
        .navigationTitle("Messages")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Menu {
                    Button("Sign out", systemImage: "rectangle.portrait.and.arrow.right") {
                        Task { await auth.signOut() }
                    }
                } label: {
                    Image(systemName: "person.crop.circle")
                        .accessibilityLabel("BLINK account menu")
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    newRecipient = ""
                    showComposer = true
                } label: {
                    Image(systemName: "square.and.pencil")
                        .accessibilityLabel("Compose a new BLINK message")
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await reload() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .accessibilityLabel("Refresh inbox")
                }
                .disabled(loading)
            }
        }
        .sheet(isPresented: $showComposer, onDismiss: { draftRecipient = nil }) {
            NavigationStack {
                BLINKNewMessageView(auth: auth, prefilledRecipient: newRecipient) {
                    showComposer = false
                    Task { await reload() }
                }
            }
        }
        .onAppear { openPendingSellerChat() }
        .onChange(of: draftRecipient) { _, _ in openPendingSellerChat() }
        .task { await reload() }
    }

    private func openPendingSellerChat() {
        guard let recipient = draftRecipient,
              let username = BLINKRecipient.normalize(recipient) else { return }
        newRecipient = username
        showComposer = true
    }

    @MainActor private func reload() async {
        guard !loading, let api else {
            if api == nil { error = "BLINK messaging is not configured." }
            return
        }
        loading = true
        defer { loading = false }
        do {
            let results = try await auth.withValidToken { token in
                try await api.conversations(token: token)
            }
            // Deduplicate conversation ids. Do not synthesize missing dialogs.
            var seen = Set<String>()
            let distinct = results.filter {
                UUID(uuidString: $0.conversationId) != nil &&
                seen.insert($0.conversationId).inserted
            }
            summaries = distinct
            // Metadata helps distinguish group conversations. A failed metadata
            // lookup must not leak data or block direct-message inbox rendering.
            if !distinct.isEmpty {
                do {
                    metadata = try await auth.withValidToken { token in
                        try await api.metadata(
                            conversationIds: distinct.map(\.conversationId),
                            token: token
                        )
                    }
                } catch {
                    metadata = [:]
                }
            } else {
                metadata = [:]
            }
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}

private struct BLINKConversationRow: View {
    let summary: BLINKConversationSummary
    let group: BLINKConversationMetadata?

    var body: some View {
        HStack(spacing: 12) {
            if let avatar = BLINKMediaURL.safe(
                group?.isGroup == true ? group?.avatarUrl : summary.partnerAvatar
            ) {
                AsyncImage(url: avatar) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Image(systemName: "person.crop.circle")
                }
                .frame(width: 48, height: 48)
                .clipShape(Circle())
            } else {
                Image(systemName: group?.isGroup == true ? "person.3.fill" : "person.crop.circle.fill")
                    .font(.system(size: 32))
                    .frame(width: 48, height: 48)
                    .foregroundStyle(.secondary)
            }
            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Text(group?.isGroup == true ? (group?.title ?? "Group chat") : summary.displayName)
                        .font(.headline)
                        .lineLimit(1)
                    Spacer()
                    if let unread = summary.unreadCount, unread > 0 {
                        Text("\(unread)")
                            .font(.caption.bold())
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(.primary, in: Capsule())
                            .foregroundStyle(Color(uiColor: .systemBackground))
                            .accessibilityLabel("\(unread) unread messages")
                    }
                }
                Text(summary.lastMessage ?? "No messages yet")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
        .padding(.vertical, 8)
    }
}

private struct BLINKNewMessageView: View {
    @ObservedObject var auth: BLINKAuthStore
    let prefilledRecipient: String
    let onSent: () -> Void

    @State private var username: String
    @State private var content = ""
    @State private var sending = false
    @State private var error: String?

    @Environment(\.dismiss) private var dismiss

    init(auth: BLINKAuthStore, prefilledRecipient: String, onSent: @escaping () -> Void) {
        self.auth = auth
        self.prefilledRecipient = prefilledRecipient
        self.onSent = onSent
        _username = State(initialValue: prefilledRecipient)
    }

    var body: some View {
        Form {
            Section("Recipient") {
                TextField("@username", text: $username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .accessibilityIdentifier("blinkMessageRecipient")
            }
            Section("Message") {
                TextField("Write a message", text: $content, axis: .vertical)
                    .lineLimit(3...8)
                    .accessibilityIdentifier("blinkNewMessageText")
            }
            if let error {
                Section { Text(error).foregroundStyle(.secondary) }
            }
            Section {
                Button {
                    Task { await send() }
                } label: {
                    HStack {
                        if sending { ProgressView() }
                        Text("Send message")
                    }
                }
                .disabled(sending || BLINKRecipient.normalize(username) == nil
                          || BLINKRecipient.content(content) == nil)
                .accessibilityIdentifier("blinkNewMessageSend")
            }
        }
        .navigationTitle("New message")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Cancel") { dismiss() }
            }
        }
    }

    @MainActor private func send() async {
        guard !sending else { return }
        sending = true
        error = nil
        defer { sending = false }
        guard let configuration = BLINKBackendConfiguration.fromBundle() else {
            error = BLINKMessagingError.unconfigured.localizedDescription
            return
        }
        let api = BLINKMessagesAPI(configuration: configuration)
        do {
            _ = try await auth.withValidToken { token in
                try await api.sendDirect(to: username, content: content, token: token)
            }
            // Clear draft only after server acknowledgement.
            content = ""
            onSent()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
