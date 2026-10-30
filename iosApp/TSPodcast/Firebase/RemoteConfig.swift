import FirebaseRemoteConfig
import Foundation
@preconcurrency import Shared

/// Where remote values come from: Firebase Remote Config in the app, a fake in tests.
protocol RemoteConfigSource: AnyObject, Sendable {
    /// Fetches and activates the latest values; throws when the fetch fails.
    func fetchAndActivate() async throws
    /// The activated value of `key`, or nil when Remote Config has none (only the static default).
    func string(forKey key: String) -> String?
}

/// Shared `ConfigAPI` over Remote Config (Android `TSFirebaseRemoteConfig.getString`). A key with
/// no remote value reads as nil, so shared falls back to its bundled default (`blacklist.json`).
final class FirebaseRemoteConfigAPI: NSObject, ConfigAPI, Sendable {
    private let source: any RemoteConfigSource

    init(source: any RemoteConfigSource) {
        self.source = source
    }

    func getString(key: String) -> String? {
        source.string(forKey: key)
    }
}

/// `RemoteConfigSource` on `RemoteConfig.remoteConfig()`. Values activated by an earlier launch
/// are read at once (the blacklist built with the graph already sees them), like Android.
final class FirebaseRemoteConfigSource: RemoteConfigSource, @unchecked Sendable {
    private var remoteConfig: RemoteConfig { RemoteConfig.remoteConfig() }

    func fetchAndActivate() async throws {
        _ = try await remoteConfig.fetchAndActivate()
    }

    func string(forKey key: String) -> String? {
        let value = remoteConfig.configValue(forKey: key)
        guard value.source == .remote else { return nil }
        return value.stringValue
    }
}

/// Launch fetch of Remote Config (Android `TSFirebaseRemoteConfig.setup`, features-spec §1):
/// up to `RemoteConfigRetry.MAX_ATTEMPTS` (20) tries, waiting 30 s × n after failed try n. On
/// success the blacklist is read again once (`onActivated`); after the last failure the error
/// goes to the crash reporter and the bundled values stay.
///
/// Difference from Android, on purpose: any successful fetch counts, also one that activated no
/// new value. Android treats `fetchAndActivate() == false` (nothing new) as a failure and keeps
/// fetching with backoff.
struct RemoteConfigLoader: Sendable {
    static let crashTag = "RemoteConfig"

    let source: any RemoteConfigSource
    let crash: any CrashReporter
    /// `blacklistRepository.reload()`.
    let onActivated: @Sendable () -> Void
    /// Waits between tries; tests pass a recording one that returns at once.
    var sleep: @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }

    /// Runs the loop; true when a fetch succeeded. Stops early when the task is cancelled.
    @discardableResult
    func run() async -> Bool {
        var attempt: Int32 = 1
        while true {
            do {
                try await source.fetchAndActivate()
                AppLog.firebase.notice("remote_config=activated attempt=\(attempt, privacy: .public)")
                onActivated()
                return true
            } catch {
                AppLog.firebase.error("remote_config_failed attempt=\(attempt, privacy: .public) type=\(String(describing: type(of: error)), privacy: .public)")
                guard RemoteConfigRetry.shared.hasAttemptAfter(attempt: attempt) else {
                    crash.record(
                        throwable: KotlinException(message: "Remote Config fetch failed \(attempt) times: \(error.localizedDescription)"),
                        tag: Self.crashTag,
                        keys: ["attempts": String(attempt)]
                    )
                    return false
                }
                let delay = Duration.milliseconds(RemoteConfigRetry.shared.delayMillisFor(attempt: attempt))
                do {
                    try await sleep(delay)
                } catch {
                    return false
                }
                attempt += 1
            }
        }
    }
}
