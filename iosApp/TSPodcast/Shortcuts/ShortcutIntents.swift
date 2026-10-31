import AppIntents

/// The message Siri and Shortcuts show when an intent has nothing to act on.
enum ShortcutMessage: Error, CustomLocalizedStringResourceConvertible {
    /// "Chưa có tập nào để tiếp tục".
    case nothingToResume

    var localizedStringResource: LocalizedStringResource {
        switch self {
        case .nothingToResume: "shortcut.continue.nothing"
        }
    }
}

/// "Tiếp tục nghe" (M6 §3.7): plays the player's item, else the last episode listened to from
/// its saved position. Runs in the app process in the background (`AudioPlaybackIntent`).
struct ContinueListeningIntent: AudioPlaybackIntent {
    static let title: LocalizedStringResource = "shortcut.continue.title"
    static let description = IntentDescription("shortcut.continue.description")

    @MainActor
    func perform() async throws -> some IntentResult {
        let outcome = await ShortcutBridge.actions()?.continueListening() ?? .nothingToResume
        if outcome == .nothingToResume {
            throw ShortcutMessage.nothingToResume
        }
        return .result()
    }
}

/// "Tạm dừng": the user's pause, which also cancels the sleep timer (M6 §3.1).
struct PauseIntent: AudioPlaybackIntent {
    static let title: LocalizedStringResource = "shortcut.pause.title"
    static let description = IntentDescription("shortcut.pause.description")

    @MainActor
    func perform() async throws -> some IntentResult {
        _ = await ShortcutBridge.actions()?.pause()
        return .result()
    }
}

/// "Hẹn giờ tắt": a sleep timer of `minutes` (default 30, 1…180; the Player sheet only offers
/// its six presets), `sleep_timer_set` with origin `shortcut`.
struct SetSleepTimerIntent: AppIntent {
    static let title: LocalizedStringResource = "shortcut.sleepTimer.title"
    static let description = IntentDescription("shortcut.sleepTimer.description")

    @Parameter(title: "shortcut.sleepTimer.minutes", default: 30, inclusiveRange: (1, 180))
    var minutes: Int

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let set = await ShortcutBridge.actions()?.setSleepTimer(minutes: minutes)
            ?? SleepTimerSetting.clamped(minutes: minutes).minutesValue
        return .result(dialog: IntentDialog("news.toast \(set)"))
    }
}

private extension SleepTimerSetting {
    var minutesValue: Int {
        if case let .minutes(minutes) = self { return minutes }
        return Self.minMinutes
    }
}

/// The phrases Siri and Spotlight offer without setup. Every phrase names the app
/// (`\(.applicationName)`); translations are in `AppShortcuts.xcstrings` (vi + en). English Siri
/// works from iOS 17; Vietnamese only when Siri speaks Vietnamese (iOS 18.4+).
struct TSPodcastShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: ContinueListeningIntent(),
            phrases: ["Continue listening on \(.applicationName)"],
            shortTitle: "shortcut.continue.title",
            systemImageName: "play.fill"
        )
        AppShortcut(
            intent: PauseIntent(),
            phrases: ["Pause \(.applicationName)"],
            shortTitle: "shortcut.pause.title",
            systemImageName: "pause.fill"
        )
        AppShortcut(
            intent: SetSleepTimerIntent(),
            phrases: ["Set a sleep timer in \(.applicationName)"],
            shortTitle: "shortcut.sleepTimer.title",
            systemImageName: "moon.zzz"
        )
    }
}
