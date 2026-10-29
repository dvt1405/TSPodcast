import Foundation

/// Hooks that XCUITest passes as launch arguments or environment (M1 §4.7).
///
/// Every hook is read inside `#if DEBUG` only: a Release build never looks at the arguments and
/// its binary does not even contain their names (M1-AC10). In Release every flag is false.
struct LaunchOptions: Equatable, Sendable {
    /// `-uitest-reset`: clear the key-value store and the artwork caches before routing.
    var resetState = false
    /// `-uitest-skip-onboarding`: reset, then finish onboarding and save categories 1, 2, 3
    /// through the shared `FavouriteCategories.save`, so the app opens on Main.
    var skipOnboarding = false
    /// `-uitest-miniplayer-placeholder`: show a fake bar in the MiniPlayer slot.
    var miniPlayerPlaceholder = false
    /// `-uitest-slow-network`: Home's data source waits (`HomeFeedDataSource.slowNetworkDelay`) before every shared call.
    var slowNetwork = false
    /// Env `TSP_UITEST_BASE_URL`: PodcastIndex base URL used instead of the one in Info.plist.
    var baseURLOverride: String?

    /// Whether the store must be cleared before reading the start destination.
    var shouldReset: Bool { resetState || skipOnboarding }

    static let none = LaunchOptions()

    /// Options of the running process.
    static let current = LaunchOptions(
        arguments: ProcessInfo.processInfo.arguments,
        environment: ProcessInfo.processInfo.environment
    )

    init() {}

    init(arguments: [String], environment: [String: String]) {
        #if DEBUG
        resetState = arguments.contains("-uitest-reset")
        skipOnboarding = arguments.contains("-uitest-skip-onboarding")
        miniPlayerPlaceholder = arguments.contains("-uitest-miniplayer-placeholder")
        slowNetwork = arguments.contains("-uitest-slow-network")
        let baseURL = environment["TSP_UITEST_BASE_URL"]?.trimmingCharacters(in: .whitespacesAndNewlines)
        baseURLOverride = (baseURL?.isEmpty ?? true) ? nil : baseURL
        #endif
    }
}
