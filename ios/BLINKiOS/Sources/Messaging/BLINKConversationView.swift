import SwiftUI

/// Native DM and group thread viewer. Only server-returned messages render.
/// No optimistic bubbles or local message history that can outlive sign-out.
struct BLINKConversationView: View {
    @ObservedObject var auth: BLINKAuthStore
    let userID: String
    let summary: BLINKConversationSummary
    let group: BLINKConversationMetadata?

    @State private var messages: [BLINKChatMessage] = []
    @State private var olderTime: String?
    @State private var olderID: String?
    @State private var hasMore = false
    @State private var loading = false
    @State private var loadingOlder = false
    @State private var sending = false
    @State private var draft = ""
    @State private var error: String?

    private var isGroup: Bool { group?.isGroup == true }

    private var conversationTitle: String {
        isGroup ? (group?.title ?? "Group chat") : summary.displayName
    }

    private var canSend: Bool {
        BLINKRecipient.content(draft) != nil &&
        (isGroup || BLINKRecipient.normalize(summary.partnerUsername ?? "") != nil)
    }

    private var api: BLINKMessagesAPI? {
        BLINKBackendConfiguration.fromBundle().map { BLINKMessagesAPI(configuration: $0) }
    }

    var body: some View {
        VStack(spacing: 0) {
            if let error {
                HStack {
                    Image(systemName: "exclamationmark.circle")
                    Text(error)
                        .lineLimit(3)
                    Spacer()
                    Button("Retry") { Task { await reload(silent: false) } }
                }
                .font(.caption)
                .padding(10)
                .background(.regularMaterial)
            }

            if loading && messages.isEmpty {
                ProgressView("Loading messages…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollViewReader { reader in
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            if hasMore {
                                Button {
                                    Task { await loadOlder() }
                                } label: {
                                    if loadingOlder { ProgressView() }
                                    else { Text("Load older messages") }
                                }
                                .disabled(loadingOlder)
                                .padding(12)
                            }

                            if messages.isEmpty {
                                ContentUnavailableView(
                                    "No messages yet",
                                    systemImage: "bubble.left.and.bubble.right",
                                    description: Text("Start a conversation on BLINK.")
                                )
                                .padding(.top, 50)
                            }

                            ForEach(messages) { message in
                                BLINKMessageBubble(
                                    message: message,
                                    mine: message.senderId == userID
                                )
                                .id(message.id)
                            }
                        }
                        .padding(14)
                    }
                    .refreshable { await reload(silent: false) }
                    .onChange(of: messages.last?.id) { _, newest in
                        if let newest {
                            withAnimation(.easeOut(duration: 0.2)) {
                                reader.scrollTo(newest, anchor: .bottom)
                            }
                        }
                    }
                }
            }

            Divider()

            HStack(alignment: .bottom, spacing: 10) {
                TextField("Message", text: $draft, axis: .vertical)
                    .lineLimit(1...5)
                    .textFieldStyle(.roundedBorder)
                    .accessibilityIdentifier("blinkChatComposer")

                Button {
                    Task { await send() }
                } label: {
                    if sending {
                        ProgressView()
                    } else {
                        Image(systemName: "arrow.up.circle.fill")
                            .font(.title2)
                            .accessibilityLabel("Send BLINK message")
                    }
                }
                .disabled(sending || !canSend)
                .accessibilityIdentifier("blinkChatSend")
            }
            .padding(12)
        }
        .background(Color(uiColor: .systemBackground))
        .navigationTitle(conversationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await reload(silent: false) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .accessibilityLabel("Refresh chat")
                }
            }
        }
        .task {
            await reload(silent: false)
        }
        // While a thread is actively visible, refresh periodically. No private
        // message polling continues in the background after the view disappears.
        .task {
            while !Task.isCancelled {
                do { try await Task.sleep(for: .seconds(20)) }
                catch { break }
                if Task.isCancelled { break }
                await reload(silent: true)
            }
        }
    }

    @MainActor private func reload(silent: Bool) async {
        guard !loading, !sending, let api else {
            if api == nil { error = BLINKMessagingError.unconfigured.localizedDescription }
            return
        }
        loading = !silent
        defer { loading = false }
        do {
            let response = try await auth.withValidToken { token in
                try await api.messages(conversationID: summary.conversationId, token: token)
            }
            let previousLatest = messages.last?.id
            messages = response.messages
            olderTime = response.beforeTime
            olderID = response.beforeId
            hasMore = response.hasMore
            error = nil

            if !silent || previousLatest != messages.last?.id {
                await markRead(api)
            }
        } catch {
            if !silent { self.error = error.localizedDescription }
        }
    }

    @MainActor private func loadOlder() async {
        guard !loadingOlder, hasMore, let api else { return }
        loadingOlder = true
        defer { loadingOlder = false }
        do {
            let page = try await auth.withValidToken { token in
                try await api.messages(
                    conversationID: summary.conversationId,
                    before: olderTime, beforeID: olderID, token: token
                )
            }
            let seen = Set(messages.map(\.id))
            messages.insert(contentsOf: page.messages.filter { !seen.contains($0.id) }, at: 0)
            olderTime = page.beforeTime
            olderID = page.beforeId
            hasMore = page.hasMore
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    @MainActor private func send() async {
        guard !sending, let content = BLINKRecipient.content(draft), let api else { return }
        sending = true
        error = nil
        defer { sending = false }
        do {
            if isGroup {
                try await auth.withValidToken { token in
                    try await api.sendGroup(
                        conversationID: summary.conversationId,
                        content: content, token: token
                    )
                }
            } else {
                guard let username = summary.partnerUsername else {
                    throw BLINKMessagingError.invalidRecipient
                }
                _ = try await auth.withValidToken { token in
                    try await api.sendDirect(
                        to: username, content: content, token: token
                    )
                }
            }
            draft = ""
        } catch {
            self.error = error.localizedDescription
            return
        }
        // The sending gate must be released before reloading this thread.
        // Otherwise a successful send would leave the new message invisible
        // until the next manual/poll refresh.
        sending = false
        // Wait for server acknowledgement, then fetch authoritative messages.
        await reload(silent: false)
    }

    @MainActor private func markRead(_ api: BLINKMessagesAPI) async {
        do {
            if isGroup {
                try await auth.withValidToken { token in
                    try await api.markGroupRead(
                        conversationID: summary.conversationId,
                        userID: userID, token: token
                    )
                }
            } else if let recipient = summary.partnerUsername {
                try await auth.withValidToken { token in
                    try await api.markDirectRead(partner: recipient, token: token)
                }
            }
        } catch {
            // A failed receipt must not conceal messages or pretend read status.
            // Retries can occur on the next refresh.
        }
    }
}

private struct BLINKMessageBubble: View {
    let message: BLINKChatMessage
    let mine: Bool

    var body: some View {
        HStack {
            if mine { Spacer(minLength: 48) }
            VStack(alignment: .leading, spacing: 6) {
                if !message.displayedContent.isEmpty {
                    Text(message.displayedContent)
                        .textSelection(.enabled)
                        .font(.body)
                }
                if let attachment = message.attachment {
                    Link(destination: attachment) {
                        Label("Open attachment", systemImage: "paperclip")
                    }
                    .font(.caption)
                }
                if message.editedAt != nil && message.deletedForEveryone != true {
                    Text("Edited")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(12)
            .background(
                mine ? Color(uiColor: .tertiarySystemGroupedBackground)
                     : Color(uiColor: .secondarySystemGroupedBackground),
                in: RoundedRectangle(cornerRadius: 14)
            )
            if !mine { Spacer(minLength: 48) }
        }
        .accessibilityElement(children: .combine)
    }
}
