import Foundation

/// The App Shortcuts of the app (M6 §3.7), as the `intent` parameter of `shortcut_invoked`.
enum ShortcutIntentKind: String, Sendable, CaseIterable {
    case continueListening = "continue_listening"
    case pause
    case setSleepTimer = "set_sleep_timer"
}

/// What "Tiếp tục nghe" did.
enum ContinueListeningOutcome: Equatable, Sendable {
    /// The player had an item: it plays again from where it is.
    case resumed
    /// The player was empty: the last saved episode started from its saved position.
    case started
    /// Nothing in the player and nothing saved ("Chưa có tập nào để tiếp tục").
    case nothingToResume
}

/// What "Tạm dừng" did.
enum PauseOutcome: Equatable, Sendable {
    case paused
    case nothingPlaying
}

/// What the three intents do. The intents only call this, through `ShortcutBridge`; the logic is
/// here so `ShortcutActionsTests` checks it with fakes (no AVFoundation, no shared module).
@MainActor
protocol ShortcutActions: AnyObject {
    func continueListening() async -> ContinueListeningOutcome
    func pause() -> PauseOutcome
    /// Sets a sleep timer of `minutes`, brought into 1…180; returns the minutes actually set.
    func setSleepTimer(minutes: Int) -> Int
}

/// The player as the shortcuts see it (`AudioEngine` in the app, a recorder in tests).
/// `Resume` is what `ShortcutHistory` hands back to start: a `PlaybackRequest` in the app.
@MainActor
protocol ShortcutPlayer: AnyObject {
    associatedtype Resume: Sendable

    /// The player holds an item (playing, paused or failed).
    var hasCurrentItem: Bool { get }
    /// Plays the current item again (the Player's Play).
    func play()
    /// Starts `resume`, replacing what the player holds.
    func start(_ resume: Resume)
    /// The user's pause, which also cancels the sleep timer.
    func pause()
    /// `AudioEngine.setSleepTimer`, which logs `sleep_timer_set` with `origin`.
    func setSleepTimer(_ setting: SleepTimerSetting, origin: String)
}

/// The last episode the user listened to, from the saved history (`extra:current_item_id` and
/// the saved rows); nil when nothing is saved or the rows are gone.
@MainActor
protocol ShortcutHistory: AnyObject {
    associatedtype Resume: Sendable

    func lastPlayed() async -> Resume?
}

/// The analytics names the shortcuts use; `ShortcutEvents.app` takes them from shared
/// `AnalyticsEvents`, so this file stays free of the shared module.
struct ShortcutEvents: Equatable, Sendable {
    /// `shortcut_invoked`.
    let invoked: String
    /// Its parameter `intent` (`ShortcutIntentKind.rawValue`).
    let intentParameter: String
    /// `origin` of `sleep_timer_set` for a timer set by a shortcut.
    let sleepTimerOrigin: String
}

/// `ShortcutActions` over the player, the saved history and analytics:
/// - continue: the player's item if it has one, else the last saved episode (its saved list,
///   from its saved position), else `.nothingToResume`;
/// - pause: the user's pause (cancels the sleep timer), only when there is an item;
/// - sleep timer: `minutes` clamped to 1…180, origin `shortcut`.
///
/// Every run logs `shortcut_invoked` (`intent`) once, whatever the outcome.
@MainActor
final class ShortcutActionHandler<Player: ShortcutPlayer, History: ShortcutHistory>: ShortcutActions
where Player.Resume == History.Resume {
    private let player: Player
    private let history: History
    private let events: ShortcutEvents
    private let track: @MainActor (_ name: String, _ params: [String: String]) -> Void

    init(
        player: Player,
        history: History,
        events: ShortcutEvents,
        track: @escaping @MainActor (_ name: String, _ params: [String: String]) -> Void
    ) {
        self.player = player
        self.history = history
        self.events = events
        self.track = track
    }

    func continueListening() async -> ContinueListeningOutcome {
        invoked(.continueListening)
        let outcome: ContinueListeningOutcome
        if player.hasCurrentItem {
            player.play()
            outcome = .resumed
        } else if let resume = await history.lastPlayed() {
            // Something may have started while the history was read: play that instead.
            if player.hasCurrentItem {
                player.play()
                outcome = .resumed
            } else {
                player.start(resume)
                outcome = .started
            }
        } else {
            outcome = .nothingToResume
        }
        AppLog.playback.notice("shortcut continue outcome=\(String(describing: outcome), privacy: .public)")
        return outcome
    }

    func pause() -> PauseOutcome {
        invoked(.pause)
        guard player.hasCurrentItem else {
            AppLog.playback.notice("shortcut pause outcome=nothingPlaying")
            return .nothingPlaying
        }
        player.pause()
        return .paused
    }

    func setSleepTimer(minutes: Int) -> Int {
        invoked(.setSleepTimer)
        let setting = SleepTimerSetting.clamped(minutes: minutes)
        player.setSleepTimer(setting, origin: events.sleepTimerOrigin)
        guard case let .minutes(set) = setting else { return SleepTimerSetting.minMinutes }
        return set
    }

    private func invoked(_ kind: ShortcutIntentKind) {
        track(events.invoked, [events.intentParameter: kind.rawValue])
    }
}
