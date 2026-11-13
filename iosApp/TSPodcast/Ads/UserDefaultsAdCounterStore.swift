import Foundation
@preconcurrency import Shared

/// Shared `AdCounterStore` on `UserDefaults` (Android `SharedPrefsAdCounterStore`): install time,
/// session count, last full-screen ad, per-placement last/daily counts survive relaunches;
/// per-session counts live in memory. One session per process however often `startSession` runs.
final class UserDefaultsAdCounterStore: NSObject, AdCounterStore, @unchecked Sendable {
    static let prefix = "ads."

    private let defaults: UserDefaults
    private let lock = NSLock()
    private var sessionShows: [String: Int32] = [:]
    private var sessionStarted = false

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    private func key(_ name: String) -> String { Self.prefix + name }

    private func int64(_ name: String) -> Int64 {
        (defaults.object(forKey: key(name)) as? NSNumber)?.int64Value ?? 0
    }

    private func set(_ value: Int64, _ name: String) {
        defaults.set(NSNumber(value: value), forKey: key(name))
    }

    func installAt(now: Int64) -> Int64 {
        lock.withLock {
            let stored = int64("install_at")
            if stored > 0 { return stored }
            set(now, "install_at")
            return now
        }
    }

    var sessionCount: Int32 { lock.withLock { Int32(clamping: int64("sessions")) } }
    var lastFullScreenAt: Int64 { lock.withLock { int64("last_full_screen") } }
    var adFreeUntil: Int64 { lock.withLock { int64("ad_free_until") } }

    func lastShownAt(placement: AdPlacement) -> Int64 {
        lock.withLock { int64("last_\(placement.key)") }
    }

    func shownOnDay(placement: AdPlacement, day: Int64) -> Int32 {
        lock.withLock { shownOnDayLocked(placement, day) }
    }

    private func shownOnDayLocked(_ placement: AdPlacement, _ day: Int64) -> Int32 {
        guard defaults.object(forKey: key("day_\(placement.key)")) != nil, int64("day_\(placement.key)") == day else { return 0 }
        return Int32(clamping: int64("count_\(placement.key)"))
    }

    func shownThisSession(placement: AdPlacement) -> Int32 {
        lock.withLock { sessionShows[placement.key] ?? 0 }
    }

    func recordShown(placement: AdPlacement, now: Int64, day: Int64) {
        lock.withLock {
            let today = shownOnDayLocked(placement, day)
            sessionShows[placement.key, default: 0] += 1
            set(now, "last_\(placement.key)")
            set(day, "day_\(placement.key)")
            set(Int64(today) + 1, "count_\(placement.key)")
            if placement.isFullScreen { set(now, "last_full_screen") }
        }
    }

    func startSession() {
        lock.withLock {
            guard !sessionStarted else { return }
            sessionStarted = true
            set(int64("sessions") + 1, "sessions")
        }
    }

    func grantAdFreeUntil(until: Int64) {
        lock.withLock { set(until, "ad_free_until") }
    }
}

/// Wall clock and the device time zone for the policy's day caps.
final class SystemAdClock: NSObject, AdClock, Sendable {
    func nowMillis() -> Int64 {
        Int64((Date().timeIntervalSince1970 * 1000).rounded())
    }

    func utcOffsetMillis(atMillis: Int64) -> Int64 {
        let date = Date(timeIntervalSince1970: TimeInterval(atMillis) / 1000)
        return Int64(TimeZone.current.secondsFromGMT(for: date)) * 1000
    }
}
