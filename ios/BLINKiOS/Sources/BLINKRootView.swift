import SwiftUI

struct BLINKRootView: View {
    private var currentUserID: String? {
        if case .signedIn(let user) = auth.state { return user.id }
        return nil
    }

    @StateObject private var auth = BLINKAuthStore()
    @StateObject private var systemCalls = BLINKCallKitManager.shared
    @State private var selectedSection: BLINKSection = .home
    @State private var sellerMessageRecipient: String?

    var body: some View {
        Group {
            switch auth.state {
            case .starting:
                ProgressView("Checking your BLINK account…")
                    .padding()
            case .unavailable:
                ContentUnavailableView {
                    Label("Connection unavailable", systemImage: "wifi.exclamationmark")
                } description: {
                    Text("BLINK could not load a valid public Supabase configuration.")
                } actions: {
                    Link("Open BLINK website", destination: URL(string: "https://www.blink.com.ng/")!)
                        .buttonStyle(.borderedProminent)
                }
            case .signedOut:
                BLINKAuthView(auth: auth)
            case .signedIn(let user):
                TabView(selection: $selectedSection) {
                    ForEach(BLINKSection.allCases) { section in
                        NavigationStack {
                            switch section {
                            case .home:
                                BLINKFeedView(mode: .posts, auth: auth)
                            case .reels:
                                BLINKFeedView(mode: .reels, auth: auth)
                            case .market:
                                BLINKMarketView(auth: auth) { username in
                                    sellerMessageRecipient = username
                                    selectedSection = .message
                                }
                            case .connect:
                                BLINKConnectView(auth: auth, currentUserID: user.id)
                            case .message:
                                BLINKMessagesView(
                                    auth: auth, userID: user.id,
                                    draftRecipient: $sellerMessageRecipient
                                )
                            default:
                                BLINKSectionView(
                                    section: section,
                                    email: user.email,
                                    signOut: { Task { await auth.signOut() } }
                                )
                            }
                        }
                        .tabItem { Label(section.title, systemImage: section.symbol) }
                        .tag(section)
                    }
                }
                .tint(.primary)
            }
        }
        .task { await auth.restore() }
        .task(id: currentUserID) {
            if let userID = currentUserID {
                systemCalls.setAccount(auth: auth, userID: userID)
                BLINKVoIPPushManager.shared.bindUser(auth: auth, userID: userID)
            } else {
                systemCalls.signOut()
                BLINKVoIPPushManager.shared.clearUser()
            }
        }
        .sheet(item: Binding(
            get: { systemCalls.presentation },
            set: { value in
                if value == nil { systemCalls.dismissPresentation() }
            }
        )) { presentation in
            if let live = systemCalls.activeSession {
                BLINKCallSessionView(coordinator: live, callKitID: presentation.id)
            } else {
                ContentUnavailableView("Call unavailable", systemImage: "phone.down")
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            Task { await auth.revalidate() }
        }
    }
}

private struct BLINKSectionView: View {
    let section: BLINKSection
    let email: String?
    let signOut: () -> Void
    private let website = URL(string: "https://www.blink.com.ng/")!

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack {
                Text("BLINK")
                    .font(.system(size: 27, weight: .black))
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                Menu {
                    if let email { Text(email) }
                    Button("Sign out", systemImage: "rectangle.portrait.and.arrow.right", action: signOut)
                } label: {
                    Image(systemName: "person.crop.circle")
                        .font(.system(size: 24))
                        .accessibilityLabel("BLINK account menu")
                }
                Link(destination: website) {
                    Image(systemName: "globe")
                        .font(.system(size: 20))
                        .accessibilityLabel("Open BLINK website")
                }
            }
            .padding(.top, 12)

            Spacer()

            ContentUnavailableView {
                Label(section.title, systemImage: section.symbol)
                    .font(.title2.weight(.semibold))
            } description: {
                Text(section.description)
            } actions: {
                Link("Open BLINK on the web", destination: website)
                    .buttonStyle(.borderedProminent)
            }

            Spacer()

            Text("Your BLINK account is connected • iOS Phase 2")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
                .padding(.bottom, 10)
        }
        .padding(.horizontal, 20)
        .background(Color(uiColor: .systemBackground))
        .navigationBarHidden(true)
    }
}
