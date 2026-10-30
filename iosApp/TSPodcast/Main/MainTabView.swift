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
    /// Favourite list and its stack (Podcast detail of a tapped favourite).
    @State private var favouriteModel: FavouriteModel
    @State private var homePath: [PodcastRoute] = []
    @State private var homeModel: HomeModel
    /// Search stack: Podcast detail and category lists (two value types).
    @State private var searchPath = NavigationPath()
    @State private var searchModel: SearchModel

    init(favouriteModel: FavouriteModel, homeModel: HomeModel, searchModel: SearchModel) {
        _favouriteModel = State(initialValue: favouriteModel)
        _homeModel = State(initialValue: homeModel)
        _searchModel = State(initialValue: searchModel)
    }

    @Environment(\.scenePhase) private var scenePhase

    /// While the player holds an item and the Player is closed (features-spec §3.7).
    private var showsMiniPlayer: Bool {
        environment.audioEngine.state.current != nil && !environment.isPlayerPresented
    }

    var body: some View {
        @Bindable var environment = environment
        TabView(selection: $selection) {
            NavigationStack(path: $favouriteModel.path) {
                FavouriteRootView(model: favouriteModel) { selection = .home }
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

            NavigationStack(path: $searchPath) {
                SearchView(model: searchModel)
                    .tabRoot(title: "tab.search")
            }
            .miniPlayerInset(isPresented: showsMiniPlayer)
            .tabItem { Label("tab.search", systemImage: "magnifyingglass") }
            .tag(AppTab.search)
        }
        .miniPlayerAccessory(isPresented: showsMiniPlayer)
        .fullScreenCover(isPresented: $environment.isPlayerPresented) {
            PlayerView(engine: environment.audioEngine)
                .environment(environment)
                .environment(\.artworkLoader, environment.artworkLoader)
                .tint(Color.brandPrimary)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                environment.audioEngine.persistPosition()
            }
        }
        #if DEBUG
        .overlay(alignment: .topLeading) {
            if environment.launchOptions.playbackProbe {
                PlaybackProbe(engine: environment.audioEngine)
            }
        }
        #endif
    }
}

private extension View {
    func tabRoot(title: LocalizedStringKey) -> some View {
        navigationTitle(Text(title))
            .toolbarTitleDisplayMode(.inlineLarge)
    }
}
