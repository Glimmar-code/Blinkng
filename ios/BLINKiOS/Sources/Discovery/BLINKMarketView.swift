import SwiftUI

/// Browses only records allowed by BLINK's ranked marketplace RPC.
/// Listing creation, approvals and payments remain disabled on iOS until
/// verified server-enforced seller and payment flows are ready.
struct BLINKMarketView: View {
    @ObservedObject var auth: BLINKAuthStore
    @State private var query = ""
    @State private var listings: [BLINKMarketplaceListing] = []
    @State private var loading = false
    @State private var error: String?
    @State private var requestNumber = 0

    private var api: BLINKDiscoveryAPI? {
        BLINKBackendConfiguration.fromBundle().map { BLINKDiscoveryAPI(configuration: $0) }
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 12) {
                    Text("Market")
                        .font(.title2.bold())
                    Spacer()
                    Button {
                        Task { await reload() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .accessibilityLabel("Refresh marketplace")
                    }
                    .disabled(loading)
                }

                HStack(spacing: 8) {
                    TextField("Search approved listings", text: $query)
                        .textFieldStyle(.roundedBorder)
                        .submitLabel(.search)
                        .textInputAutocapitalization(.never)
                        .onSubmit { Task { await reload() } }
                        .accessibilityIdentifier("blinkMarketSearch")
                    Button("Search") {
                        Task { await reload() }
                    }
                    .disabled(loading)
                    .buttonStyle(.bordered)
                }

                if loading {
                    ProgressView("Loading marketplace…")
                        .frame(maxWidth: .infinity, minHeight: 160)
                } else if let error {
                    ContentUnavailableView {
                        Label("Marketplace unavailable", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text(error)
                    } actions: {
                        Button("Retry") { Task { await reload() } }
                            .buttonStyle(.borderedProminent)
                    }
                } else if listings.isEmpty {
                    ContentUnavailableView {
                        Label("No items found", systemImage: "bag")
                    } description: {
                        Text("No marketplace listings match your search right now.")
                    }
                } else {
                    ForEach(listings) { item in
                        BLINKMarketCard(item: item)
                    }
                    Text("Seller registration and paid checkout are not available in this iOS preview.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding(.vertical, 10)
                }
            }
            .padding(16)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .refreshable { await reload() }
        .task { await reload() }
    }

    @MainActor private func reload() async {
        requestNumber += 1
        let currentRequest = requestNumber
        loading = true
        error = nil
        defer {
            if currentRequest == requestNumber { loading = false }
        }
        guard let api else {
            error = "BLINK marketplace connection is not configured."
            return
        }
        do {
            let value = query.trimmingCharacters(in: .whitespacesAndNewlines)
            let rows = try await auth.withValidToken { token in
                try await api.marketplace(query: String(value.prefix(100)), token: token)
            }
            guard currentRequest == requestNumber else { return }
            listings = rows
        } catch {
            guard currentRequest == requestNumber else { return }
            self.error = error.localizedDescription
        }
    }
}

private struct BLINKMarketCard: View {
    let item: BLINKMarketplaceListing

    private var sellerMessageURL: URL? {
        guard let username = item.sellerHandle,
              var parts = URLComponents(string: "https://www.blink.com.ng/messages/new") else {
            return nil
        }
        parts.queryItems = [URLQueryItem(name: "user", value: username)]
        return parts.url
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let imageURL = item.coverImage {
                AsyncImage(url: imageURL) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color(uiColor: .tertiarySystemGroupedBackground))
                        .overlay(ProgressView())
                }
                .frame(height: 200)
                .frame(maxWidth: .infinity)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .accessibilityLabel(item.title ?? "Marketplace listing photo")
            }

            Text(item.title?.isEmpty == false ? item.title! : "Marketplace item")
                .font(.headline)
            Text(item.displayPrice)
                .font(.title3.weight(.bold))

            let metadata = [item.condition, item.location ?? item.university]
                .compactMap { $0 }
                .filter { !$0.isEmpty }
            if !metadata.isEmpty {
                Text(metadata.joined(separator: " · "))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if let description = item.description, !description.isEmpty {
                Text(description)
                    .font(.subheadline)
                    .lineLimit(4)
            }

            if let link = sellerMessageURL {
                Link("Message seller on BLINK web", destination: link)
                    .font(.subheadline.weight(.semibold))
                    .buttonStyle(.bordered)
            } else {
                Text("Seller contact not available")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
    }
}
