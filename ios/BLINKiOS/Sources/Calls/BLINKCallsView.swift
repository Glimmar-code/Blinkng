import SwiftUI

/// Honest call history/state UI. Do not show "Answer" or a fake call duration
/// until actual bidirectional iOS voice/video transport is implemented.
struct BLINKCallsView: View {
    @ObservedObject var auth: BLINKAuthStore
    let userID: String
    let conversationID: String?

    @State private var recent: [BLINKCallRecord] = []
    @State private var error: String?
    @State private var loading = false
    @State private var pending: Set<String> = []

    private var api: BLINKCallsAPI? {
        BLINKBackendConfiguration.fromBundle().map {
            BLINKCallsAPI(configuration: $0)
        }
    }

    private var filtered: [BLINKCallRecord] {
        recent.filter { call in
            conversationID == nil || call.conversationId == conversationID
        }
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 15) {
                VStack(alignment: .leading, spacing: 7) {
                    Text("Calls")
                        .font(.title2.bold())
                    Text("Voice and video connection is not available in this iOS preview. You can review BLINK call history and decline existing incoming requests.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                HStack {
                    Label("Voice", systemImage: "phone")
                    Label("Video", systemImage: "video")
                }
                .font(.caption)
                .foregroundStyle(.secondary)

                if loading && recent.isEmpty {
                    ProgressView("Checking recent calls…")
                        .frame(maxWidth: .infinity, minHeight: 140)
                } else if let error {
                    ContentUnavailableView {
                        Label("Calls unavailable", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text(error)
                    } actions: {
                        Button("Try again") { Task { await reload() } }
                            .buttonStyle(.bordered)
                    }
                } else if filtered.isEmpty {
                    ContentUnavailableView(
                        "No calls yet",
                        systemImage: "phone",
                        description: Text("Recent BLINK call requests will appear here.")
                    )
                } else {
                    ForEach(filtered) { call in
                        VStack(alignment: .leading, spacing: 9) {
                            HStack {
                                Image(systemName: call.callType == .video ? "video" : "phone")
                                Text(call.callType.title)
                                    .font(.headline)
                                Spacer()
                                Text(call.isIncoming(for: userID) ? "Incoming" : "Outgoing")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Text(call.status.rawValue.capitalized)
                                .font(.subheadline)
                            if call.isIncoming(for: userID) && call.canDecline {
                                Button("Decline ringing call") {
                                    Task { await decline(call.id) }
                                }
                                .buttonStyle(.bordered)
                                .disabled(pending.contains(call.id))
                            } else if call.canEnd && call.isOutgoing(for: userID) {
                                Button("Cancel call") {
                                    Task { await end(call.id) }
                                }
                                .buttonStyle(.bordered)
                                .disabled(pending.contains(call.id))
                            }
                            if call.status == .ringing {
                                Text("Answer unavailable: secure media transport isn't connected on iOS.")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(uiColor: .secondarySystemGroupedBackground),
                                    in: RoundedRectangle(cornerRadius: 14))
                    }
                }
            }
            .padding(15)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("BLINK Calls")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await reload() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .accessibilityLabel("Refresh calls")
                }
            }
        }
        .refreshable { await reload() }
        .task { await reload() }
    }

    @MainActor private func reload() async {
        guard !loading, let api else {
            if api == nil { error = BLINKCallError.notAvailable.localizedDescription }
            return
        }
        loading = true
        defer { loading = false }
        do {
            recent = try await auth.withValidToken { token in
                try await api.recent(token: token)
            }
            error = nil
        } catch { self.error = error.localizedDescription }
    }

    @MainActor private func decline(_ id: String) async {
        guard !pending.contains(id), let api else { return }
        pending.insert(id)
        defer { pending.remove(id) }
        do {
            _ = try await auth.withValidToken { token in
                try await api.decline(callID: id, token: token)
            }
            await reload()
        } catch { self.error = error.localizedDescription }
    }

    @MainActor private func end(_ id: String) async {
        guard !pending.contains(id), let api else { return }
        pending.insert(id)
        defer { pending.remove(id) }
        do {
            _ = try await auth.withValidToken { token in
                try await api.end(callID: id, token: token)
            }
            await reload()
        } catch { self.error = error.localizedDescription }
    }
}
