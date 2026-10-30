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
    /// `-uitest-slow-network`: the Home, Detail and Search data sources wait
    /// (`HomeFeedDataSource.slowNetworkDelay`) before every shared call.
    var slowNetwork = false
    /// `-uitest-fail-detail-once`: the first Detail load of the process fails without a request.
    var failDetailOnce = false
    /// `-uitest-detail-empty`: Detail loads succeed with no episode, without a request.
    var detailEmpty = false
    /// `-uitest-detail-explicit`: Detail loads report the feed hidden by D6, without a request.
    var detailExplicit = false
    /// `-uitest-playback-probe`: Main and the Player show the player state as text (M3 §4.6).
    var playbackProbe = false
    /// `-uitest-force-live`: every played item counts as a live stream.
    var forceLive = false
    /// Env `TSP_UITEST_AUDIO_URL`: audio URL played instead of every item's own (a local fixture).
    var audioURLOverride: URL?
    /// Env `TSP_UITEST_BASE_URL`: PodcastIndex base URL used instead of the one in Info.plist.
    var baseURLOverride: String?
    /// `-uitest-consent=all|necessary` (M6 §3.6): saves that consent decision before routing, so
    /// the first-launch privacy sheet does not show.
    var consentChoice: ConsentChoice?

    enum ConsentChoice: String, Equatable, Sendable {
        case all
        case necessary
    }

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
        slowNetwork = arguments.contains("-uitest-slow-network")
        failDetailOnce = arguments.contains("-uitest-fail-detail-once")
        detailEmpty = arguments.contains("-uitest-detail-empty")
        detailExplicit = arguments.contains("-uitest-detail-explicit")
        playbackProbe = arguments.contains("-uitest-playback-probe")
        forceLive = arguments.contains("-uitest-force-live")
        let audioURL = environment["TSP_UITEST_AUDIO_URL"]?.trimmingCharacters(in: .whitespacesAndNewlines)
        audioURLOverride = audioURL.flatMap { $0.isEmpty ? nil : URL(string: $0) }
        consentChoice = arguments.lazy
            .compactMap { $0.hasPrefix("-uitest-consent=") ? ConsentChoice(rawValue: String($0.dropFirst("-uitest-consent=".count))) : nil }
            .first
        let baseURL = environment["TSP_UITEST_BASE_URL"]?.trimmingCharacters(in: .whitespacesAndNewlines)
        baseURLOverride = (baseURL?.isEmpty ?? true) ? nil : baseURL
        #endif
    }
}
