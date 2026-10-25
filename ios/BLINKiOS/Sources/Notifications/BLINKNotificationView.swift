import SwiftUI

struct BLINKNotificationView: View {
    @ObservedObject var auth: BLINKAuthStore
    @State private var items: [BLINKNotification] = []
    @State private var loading = true
    @State private var error: String?
    @Environment(\.dismiss) private var dismiss

    private var api: BLINKNotificationAPI? {
        BLINKBackendConfiguration.fromBundle().map {
            BLINKNotificationAPI(configuration: $0)
        }
    }

    var body: some View {
        Group {
            if loading && items.isEmpty {
                ProgressView("Loading activity…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error, items.isEmpty {
                ContentUnavailableView {
                    Label("Activity unavailable", systemImage: "wifi.exclamationmark")
                } description: {
                    Text(error)
                } actions: {
                    Button("Retry") { Task { await reload() } }
                        .buttonStyle(.borderedProminent)
                }
            } else if items.isEmpty {
                ContentUnavailableView(
                    "No notifications",
                    systemImage: "bell",
                    description: Text("Your BLINK activity will appear here.")
                )
            } else {
                List(items) { notification in
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: notification.isRead == true
                              ? "bell" : "bell.badge")
                            .foregroundStyle(.primary)
                            .frame(width: 24)
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 5) {
                            Text(notification.title)
                                .font(.subheadline.weight(
                                    notification.isRead == true ? .regular : .semibold
                                ))
                            if let detail = notification.detail {
                                Text(detail)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .padding(.vertical, 6)
                }
                .listStyle(.plain)
                .refreshable { await reload() }
            }
        }
        .navigationTitle("Activity")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }
            }
        }
        .safeAreaInset(edge: .bottom) {
            Text("Notifications are read-only in this iOS preview. Background push delivery needs Apple APNs setup.")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .padding(9)
                .frame(maxWidth: .infinity)
                .background(.regularMaterial)
        }
        .task { await reload() }
    }

    @MainActor private func reload() async {
        guard !loading || items.isEmpty else { return }
        guard let api else {
            loading = false
            error = BLINKNotificationError.notConfigured.localizedDescription
            return
        }
        loading = true
        defer { loading = false }
        do {
            items = try await auth.withValidToken { token in
                try await api.list(token: token)
            }
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}
