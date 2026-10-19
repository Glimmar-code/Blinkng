import SwiftUI

struct BLINKRootView: View {
    @State private var selectedSection: BLINKSection = .home

    var body: some View {
        TabView(selection: $selectedSection) {
            ForEach(BLINKSection.allCases) { section in
                NavigationStack {
                    BLINKSectionView(section: section)
                }
                .tabItem {
                    Label(section.title, systemImage: section.symbol)
                }
                .tag(section)
            }
        }
        .tint(.primary)
    }
}

private struct BLINKSectionView: View {
    let section: BLINKSection
    private let website = URL(string: "https://www.blink.com.ng/")!

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack {
                Text("BLINK")
                    .font(.system(size: 27, weight: .black))
                    .accessibilityAddTraits(.isHeader)
                Spacer()
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

            Text("iOS foundation • Phase 1")
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
