import SwiftUI

/// Tabs of the main screen in Android's order; iOS v1 has these three only (features-spec §3.3, D1).
enum AppTab: Hashable, Sendable {
    case favourite
    case home
    case search
}

/// Main shell (Android `HomeNavigationScreen` + `BottomAppBarBlur`): Yêu thích / Khám phá
/// (default) / Tìm kiếm, each with its own `NavigationStack` and a large inline title, plus the
/// MiniPlayer slot above the tab bar (`MiniPlayerSlot`: the iOS 26 accessory, or a floating
/// Material capsule on iOS 17/18).
///
/// `TabView(selection:)` with `.tabItem`/`.tag` runs on iOS 17; built with the iOS 26 SDK the tab
/// bar gets Liquid Glass. `Tab(...)` would need iOS 18.
struct MainTabView: View {
    @Environment(AppEnvironment.self) private var environment
    /// Favourite list and its stack (Podcast detail of a tapped favourite).
    @State private var favouriteModel: FavouriteModel
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
        @Bindable var navigation = environment.navigation
        @Bindable var deepLinks = environment.deepLinks
        TabView(selection: $navigation.selectedTab) {
            NavigationStack(path: $favouriteModel.path) {
                FavouriteRootView(model: favouriteModel) { navigation.selectedTab = .home }
                    .tabRoot(title: "tab.favourite")
            }
            .miniPlayerInset(isPresented: showsMiniPlayer)
            .tabItem { Label("tab.favourite", systemImage: "heart") }
            .tag(AppTab.favourite)

            NavigationStack(path: $navigation.homePath) {
                HomeView(model: homeModel)
                    .tabRoot(title: "tab.home")
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button {
                                navigation.isShowingSettings = true
                            } label: {
                                Image(systemName: "gearshape")
                            }
                            .accessibilityLabel(Text("settings.title"))
                            .accessibilityIdentifier("settings.open")
                        }
                    }
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
        // iOS 26: the tab bar shrinks while scrolling down and the MiniPlayer accessory moves
        // inline (compact layout). The tab and navigation bars keep the system glass, with no
        // custom background behind them (M5 §3.3).
        .tabBarMinimizesOnScroll()
        .miniPlayerAccessory(isPresented: showsMiniPlayer)
        .sheet(isPresented: $navigation.isShowingSettings) {
            SettingsView(model: environment.makeSettingsModel())
                .tint(Color.brandPrimary)
        }
        .fullScreenCover(isPresented: $environment.isPlayerPresented) {
            PlayerView(engine: environment.audioEngine)
                .environment(environment)
                .environment(\.artworkLoader, environment.artworkLoader)
                .tint(Color.brandPrimary)
        }
        .alert(Text("deeplink.error.title"), isPresented: $deepLinks.isShowingError) {
            Button("action.close", role: .cancel) {}
        } message: {
            Text("deeplink.error.message")
                .accessibilityIdentifier("deeplink.error")
        }
        // Links that arrived during onboarding or category selection open now (M4 §4.5).
        .task { await environment.deepLinks.mainDidAppear() }
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
