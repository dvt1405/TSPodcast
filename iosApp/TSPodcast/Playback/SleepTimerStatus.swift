import Foundation

/// What the sleep timer is set to, as views see it (shared `SleepTimerOption`; views never hold
/// Kotlin objects). `minutes` is always within `minMinutes...maxMinutes`: build it with
/// `clamped(minutes:)` from a free value.
enum SleepTimerSetting: Hashable, Sendable {
    case off
    case minutes(Int)
    case endOfEpisode

    /// Shared `SleepTimer.MIN_MINUTES` / `MAX_MINUTES`.
    static let minMinutes = 1
    static let maxMinutes = 180
    /// Shared `SleepTimer.PRESET_MINUTES`: the sheet's choices, then "End of episode" and "Off".
    static let presetMinutes = [5, 10, 15, 30, 45, 60]

    /// Every row of the sleep timer sheet, in order.
    static let sheetOptions: [SleepTimerSetting] = presetMinutes.map { .minutes($0) } + [.endOfEpisode, .off]

    /// A free value (Shortcuts) brought into range.
    static func clamped(minutes: Int) -> SleepTimerSetting {
        .minutes(min(max(minutes, minMinutes), maxMinutes))
    }

    /// `sleepTimer.option.<suffix>` (M6 §3.9): the minutes, `endOfEpisode` or `off`.
    var identifierSuffix: String {
        switch self {
        case .off: "off"
        case let .minutes(minutes): String(minutes)
        case .endOfEpisode: "endOfEpisode"
        }
    }
}

/// The sleep timer in `PlaybackState`: the setting and, for `.minutes`, when it stops. Views
/// count down from `deadline` themselves, so the state does not change every second.
struct SleepTimerStatus: Equatable, Sendable {
    var setting: SleepTimerSetting = .off
    var deadline: Date?

    var isActive: Bool { setting != .off }

    /// Milliseconds left at `now` (never negative); nil without a deadline.
    func remainingMs(at now: Date) -> Int64? {
        guard let deadline else { return nil }
        return max(0, Int64((deadline.timeIntervalSince(now) * 1000).rounded()))
    }

    /// "28:14", or "1:02:03" from one hour. Seconds round **up**, so a fresh 30 min timer reads
    /// "30:00" and the label shows "0:01" until it actually stops.
    static func clockText(remainingMs: Int64) -> String {
        let total = Int((max(0, remainingMs) + 999) / 1000)
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let seconds = total % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        }
        return String(format: "%d:%02d", minutes, seconds)
    }
}
