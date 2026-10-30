import FirebaseAnalytics
import Foundation
@preconcurrency import Shared

/// `AnalyticsTracker` on Firebase Analytics, Android `TSAnalytics` (features-spec §4):
/// `trackScreen("Onboarding", …)` logs `onboarding_screen`, `trackEvent(name, screen, …)` logs
/// `name` with `screen`; both carry `os`, `os_version`, `app_version` and `event_time_stamp`
/// (seconds) from shared `AnalyticsDefaults`. **No device identifier** (D5).
///
/// The mapping is pure (`screenEvent`, `event`) and the Firebase call is injected, so
/// `AnalyticsMappingTests` run without Firebase.
final class FirebaseAnalyticsTracker: NSObject, AnalyticsTracker, @unchecked Sendable {
    /// One logged event: Firebase name and parameters.
    struct Event {
        let name: String
        let parameters: [String: Any]
    }

    private let log: @Sendable (String, [String: Any]) -> Void
    private let osVersion: String
    private let appVersion: String
    private let now: @Sendable () -> Date

    init(
        osVersion: String = FirebaseAnalyticsTracker.systemVersion(),
        appVersion: String = FirebaseAnalyticsTracker.bundleVersion(),
        now: @escaping @Sendable () -> Date = { Date() },
        log: @escaping @Sendable (String, [String: Any]) -> Void = { name, parameters in
            Analytics.logEvent(name, parameters: parameters)
        }
    ) {
        self.osVersion = osVersion
        self.appVersion = appVersion
        self.now = now
        self.log = log
    }

    func trackScreen(screen: String, params: [String: String]) {
        let event = screenEvent(screen: screen, params: params)
        log(event.name, event.parameters)
    }

    func trackEvent(name: String, screen: String?, params: [String: String]) {
        let event = event(name: name, screen: screen, params: params)
        log(event.name, event.parameters)
    }

    /// Android `trackScreen`: `<screen lowercase>_screen`, defaults + `params`, no `screen` key.
    func screenEvent(screen: String, params: [String: String]) -> Event {
        Event(
            name: AnalyticsDefaults.shared.screenEventName(screen: screen),
            parameters: parameters(params, screen: nil)
        )
    }

    /// Android `trackEvent`: `name`, defaults + `params` + `screen` when known.
    func event(name: String, screen: String?, params: [String: String]) -> Event {
        Event(name: AnalyticsDefaults.shared.trimName(name: name), parameters: parameters(params, screen: screen))
    }

    private func parameters(_ params: [String: String], screen: String?) -> [String: Any] {
        AnalyticsDefaults.shared.eventParams(
            params: params,
            osVersion: osVersion,
            appVersion: appVersion,
            nowSeconds: Int64(now().timeIntervalSince1970),
            screen: screen
        )
    }

    /// "17.0.1" (Android sends `Build.VERSION.SDK_INT`; iOS has no API level).
    static func systemVersion() -> String {
        let version = ProcessInfo.processInfo.operatingSystemVersion
        return "\(version.majorVersion).\(version.minorVersion).\(version.patchVersion)"
    }

    /// `CFBundleShortVersionString` (Android `versionName`).
    static func bundleVersion(_ bundle: Bundle = .main) -> String {
        bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }
}
