import SwiftUI

/// Tabs of the main screen in Android's order; iOS v1 has these three only (features-spec §3.3, D1).
enum AppTab: Hashable, Sendable {
    case favourite
    case home
    case search
}

/// Main shell. M1 T2 scaffold: three tabs, each with its own `NavigationStack` and an empty
/// root; T4 adds Home/Discover, the Favourite empty state, the Detail stub and the MiniPlayer slot.
struct MainTabView: View {
    @State private var selection: AppTab = .home

    var body: some View {
        TabView(selection: $selection) {
            tab(.favourite, title: "tab.favourite", systemImage: "heart", rootID: "favourite.root")
            tab(.home, title: "tab.home", systemImage: "safari", rootID: "home.root")
            tab(.search, title: "tab.search", systemImage: "magnifyingglass", rootID: "search.root")
        }
    }

    private func tab(_ tab: AppTab, title: LocalizedStringKey, systemImage: String, rootID: String) -> some View {
        NavigationStack {
            ScrollView {
                Color.clear.frame(height: 1)
            }
            .accessibilityIdentifier(rootID)
            .navigationTitle(Text(title))
            .toolbarTitleDisplayMode(.inlineLarge)
        }
        .tabItem { Label(title, systemImage: systemImage) }
        .tag(tab)
    }
}
