import Foundation
@preconcurrency import Shared

/// The four seams that Firebase backs when it is configured (M4 §4.3):
///
/// | Seam | Firebase | Without Firebase |
/// |---|---|---|
/// | `AnalyticsTracker` | `FirebaseAnalyticsTracker` | `LogAnalyticsTracker` |
/// | `CrashReporter` | `FirebaseCrashReporter` | `OSLogCrashReporter` |
/// | `ConfigAPI` | `FirebaseRemoteConfigAPI` (+ `RemoteConfigLoader`) | `NoOpConfig` |
/// | `PushRegistrar` | `FirebasePushRegistrar` | `LogPushRegistrar` |
///
/// The Firebase implementations are created only for `.enabled`, so a build without the plist
/// never calls a Firebase API (they assert a configured `FirebaseApp`).
struct AppServices: @unchecked Sendable {
    let analytics: any AnalyticsTracker
    let crashReporter: any CrashReporter
    let config: any ConfigAPI
    let push: any PushRegistrar
    /// What `RemoteConfigLoader` fetches at launch; nil without Firebase (bundled defaults only).
    let remoteConfig: (any RemoteConfigSource)?

    /// The log/no-op set: no Firebase API is ever called.
    static func offline() -> AppServices {
        AppServices(
            analytics: LogAnalyticsTracker(),
            crashReporter: OSLogCrashReporter(),
            config: NoOpConfig(),
            push: LogPushRegistrar(),
            remoteConfig: nil
        )
    }

    /// The Firebase set. Call only after `FirebaseBootstrap` returned `.enabled`.
    @MainActor
    static func firebase() -> AppServices {
        let remoteConfig = FirebaseRemoteConfigSource()
        return AppServices(
            analytics: FirebaseAnalyticsTracker(),
            crashReporter: FirebaseCrashReporter(),
            config: FirebaseRemoteConfigAPI(source: remoteConfig),
            push: FirebasePushRegistrar(),
            remoteConfig: remoteConfig
        )
    }

    @MainActor
    static func make(for status: FirebaseStatus) -> AppServices {
        status.isEnabled ? firebase() : offline()
    }
}
