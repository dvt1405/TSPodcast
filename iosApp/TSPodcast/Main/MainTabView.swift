import SwiftUI

/// Tabs of the main screen in Android's order; iOS v1 has these three only (features-spec §3.3, D1).
enum AppTab: Hashable, Sendable {
    case favourite
    case home
    case search
}

/// Main shell (Android `HomeNavigationScreen` + `BottomAppBarBlur`): Yêu thích / Khám phá
/// (default) / Tìm kiếm, each with its own `NavigationStack` and a large inline title, plus the
/// MiniPlayer slot above the tab bar.
///
/// `TabView(selection:)` with `.tabItem`/`.tag` runs on iOS 17; built with the iOS 26 SDK the tab
/// bar gets Liquid Glass. `Tab(...)` would need iOS 18.
struct MainTabView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var selection: AppTab = .home
    @State private var homePath: [PodcastCard] = []
    @State private var homeModel: HomeModel

    init(homeModel: HomeModel) {
        _homeModel = State(initialValue: homeModel)
    }

    /// M1 never plays anything; only the Debug hook shows the placeholder bar.
    private var showsMiniPlayer: Bool { environment.launchOptions.miniPlayerPlaceholder }

    var body: some View {
        TabView(selection: $selection) {
            NavigationStack {
                FavouriteRootView { selection = .home }
                    .tabRoot(title: "tab.favourite")
            }
            .miniPlayerInset(isPresented: showsMiniPlayer)
            .tabItem { Label("tab.favourite", systemImage: "heart") }
            .tag(AppTab.favourite)

            NavigationStack(path: $homePath) {
                HomeView(model: homeModel)
                    .tabRoot(title: "tab.home")
            }
            .miniPlayerInset(isPresented: showsMiniPlayer)
            .tabItem { Label("tab.home", systemImage: "house") }
            .tag(AppTab.home)

            NavigationStack {
                SearchRootView()
                    .tabRoot(title: "tab.search")
            }
            .miniPlayerInset(isPresented: showsMiniPlayer)
            .tabItem { Label("tab.search", systemImage: "magnifyingglass") }
            .tag(AppTab.search)
        }
        .miniPlayerAccessory(isPresented: showsMiniPlayer)
    }
}

private extension View {
    func tabRoot(title: LocalizedStringKey) -> some View {
        navigationTitle(Text(title))
            .toolbarTitleDisplayMode(.inlineLarge)
    }
}
