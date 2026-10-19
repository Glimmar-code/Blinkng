import SwiftUI

/// Read-only Connect directory + explicit, server-authorized requests.
/// No automated matchmaking acceptance, coin charge or private data reveal.
struct BLINKConnectView: View {
    @ObservedObject var auth: BLINKAuthStore
    let currentUserID: String

    @State private var mode: BLINKConnectMode = .all
    @State private var opportunities: [BLINKConnectOpportunity] = []
    @State private var loading = false
    @State private var error: String?
    @State private var requestNumber = 0
    @State private var sending: Set<String> = []
    @State private var sent: Set<String> = []

    private var api: BLINKDiscoveryAPI? {
        BLINKBackendConfiguration.fromBundle().map { BLINKDiscoveryAPI(configuration: $0) }
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 15) {
                HStack {
                    Text("Connect")
                        .font(.title2.bold())
                    Spacer()
                    Button {
                        Task { await reload() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .accessibilityLabel("Refresh Connect")
                    }
                    .disabled(loading)
                }
                .padding(.horizontal, 2)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(BLINKConnectMode.allCases) { candidate in
                            Button(candidate.title) {
                                mode = candidate
                            }
                            .buttonStyle(.bordered)
                            .tint(candidate == mode ? .primary : .secondary)
                            .accessibilityAddTraits(candidate == mode ? .isSelected : [])
                        }
                    }
                }

                if loading {
                    ProgressView("Finding campus connections…")
                        .frame(maxWidth: .infinity, minHeight: 180)
                } else if let error {
                    ContentUnavailableView {
                        Label("Connect unavailable", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text(error)
                    } actions: {
                        Button("Try again") {
                            Task { await reload() }
                        }
                        .buttonStyle(.borderedProminent)
                    }
                } else if opportunities.isEmpty {
                    ContentUnavailableView {
                        Label("No matches yet", systemImage: "person.2")
                    } description: {
                        Text("BLINK doesn't have any available \(mode.title.lowercased()) opportunities for you right now.")
                    }
                } else {
                    ForEach(Array(opportunities.enumerated()), id: \.offset) { _, item in
                        BLINKConnectCard(
                            opportunity: item,
                            currentUserID: currentUserID,
                            pending: item.receiverID.map { sending.contains($0) } ?? false,
                            requested: item.receiverID.map { sent.contains($0) } ?? false,
                            onRequest: {
                                guard let id = item.receiverID else { return }
                                Task { await sendConnectionRequest(id) }
                            }
                        )
                    }
                }
            }
            .padding(16)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .refreshable { await reload() }
        .task(id: mode) { await reload() }
    }

    @MainActor private func reload() async {
        requestNumber += 1
        let request = requestNumber
        loading = true
        error = nil
        defer { if request == requestNumber { loading = false } }
        guard let api else {
            error = "BLINK Connect is not configured."
            return
        }
        do {
            let selectedMode = mode
            let value = try await auth.withValidToken { token in
                try await api.connect(mode: selectedMode, token: token)
            }
            guard request == requestNumber else { return }
            opportunities = value
        } catch {
            guard request == requestNumber else { return }
            self.error = error.localizedDescription
        }
    }

    @MainActor private func sendConnectionRequest(_ id: String) async {
        guard id != currentUserID, !sent.contains(id), !sending.contains(id),
              let api else { return }
        sending.insert(id)
        defer { sending.remove(id) }
        do {
            try await auth.withValidToken { token in
                try await api.requestConnection(receiverID: id, token: token)
            }
            sent.insert(id)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}

private struct BLINKConnectCard: View {
    let opportunity: BLINKConnectOpportunity
    let currentUserID: String
    let pending: Bool
    let requested: Bool
    let onRequest: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text((opportunity.kind ?? "Connect").replacingOccurrences(of: "_", with: " ").capitalized)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)

            Text(opportunity.displayTitle)
                .font(.headline)

            if !opportunity.summary.isEmpty {
                Text(opportunity.summary)
                    .font(.subheadline)
                    .lineLimit(5)
            }
            if !opportunity.detail.isEmpty {
                Text(opportunity.detail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if let receiver = opportunity.receiverID, receiver != currentUserID {
                Button(action: onRequest) {
                    if pending { ProgressView() }
                    else { Text(requested ? "Request sent" : "Connect") }
                }
                .disabled(pending || requested)
                .buttonStyle(.bordered)
                .accessibilityIdentifier("blinkSendConnection")
            } else {
                Text("Connection request unavailable")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
    }
}
