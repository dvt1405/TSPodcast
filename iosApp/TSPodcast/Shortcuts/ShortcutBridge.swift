import Foundation
@preconcurrency import Shared

/// How the App Intents reach the app (M6 §3.7). iOS 17 has no `AppDependencyManager` (iOS 18),
/// so `AppEnvironment.init` registers itself here; the intents run in the app process (no
/// extension, no App Group) and the app delegate creates the environment before any intent runs.
@MainActor
enum ShortcutBridge {
    private static weak var environment: AppEnvironment?

    static func register(_ environment: AppEnvironment) {
        self.environment = environment
    }

    /// The app's `ShortcutActions`, after the saved consent is applied (a shortcut can launch the
    /// app in the background, before any screen read it), so `shortcut_invoked` follows the
    /// user's choice. Nil only if no environment exists yet.
    static func actions() async -> (any ShortcutActions)? {
        guard let environment else {
            AppLog.playback.error("shortcut_no_environment")
            return nil
        }
        await environment.consent.load()
        return environment.shortcutActions
    }
}

extension ShortcutEvents {
    /// The names of shared `AnalyticsEvents` (M6 §3.8).
    static var app: ShortcutEvents {
        let events = AnalyticsEvents.shared
        return ShortcutEvents(
            invoked: events.SHORTCUT_INVOKED,
            intentParameter: events.PARAM_INTENT,
            sleepTimerOrigin: events.ORIGIN_SHORTCUT
        )
    }
}

extension AudioEngine: ShortcutPlayer {
    var hasCurrentItem: Bool { state.current != nil }

    /// "Tiếp tục nghe" from the history: the saved list, from the saved position (the engine
    /// resumes from `PlaybackPositions`); the Player stays closed.
    func start(_ resume: PlaybackRequest) {
        play(resume)
    }
}

/// The last episode from shared `PlaybackHistory.currentItemId()` (`extra:current_item_id`) and
/// `FavouriteResolver` (the episode row → its show and every saved episode, newest first), played
/// like a favourite episode tap (features-spec §3.8).
@MainActor
final class SharedShortcutHistory: ShortcutHistory {
    private let history: PlaybackHistory
    private let resolver: FavouriteResolver

    init(history: PlaybackHistory, resolver: FavouriteResolver) {
        self.history = history
        self.resolver = resolver
    }

    func lastPlayed() async -> PlaybackRequest? {
        do {
            guard let itemId = try await history.currentItemId() else { return nil }
            guard let resolved = try await resolver.resolve(type: .podcastepisode, id: itemId) else {
                AppLog.playback.notice("shortcut_history_unresolved id=\(itemId, privacy: .public)")
                return nil
            }
            return FavouritePlayback(resolved).request
        } catch {
            AppLog.playback.error("shortcut_history_failed type=\(String(describing: type(of: error)), privacy: .public)")
            return nil
        }
    }
}
