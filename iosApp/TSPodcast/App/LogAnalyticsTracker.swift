import Foundation
@preconcurrency import Shared

/// `AnalyticsTracker` that writes each call to OSLog (category `Analytics`) until a Firebase iOS
/// app exists. Lines look like `analytics screen=Onboarding CurrentPage=2` and
/// `analytics event=OnboardingFinished screen=Onboarding`. No `device_id`, ever.
final class LogAnalyticsTracker: NSObject, AnalyticsTracker, Sendable {
    func trackScreen(screen: String, params: [String: String]) {
        let line = Self.screenLine(screen: screen, params: params)
        AppLog.analytics.notice("\(line, privacy: .public)")
    }

    func trackEvent(name: String, screen: String) {
        let line = Self.eventLine(name: name, screen: screen)
        AppLog.analytics.notice("\(line, privacy: .public)")
    }

    /// Parameters are sorted by name so a line is stable and easy to grep.
    static func screenLine(screen: String, params: [String: String]) -> String {
        let pairs = params.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }
        return (["analytics screen=\(screen)"] + pairs).joined(separator: " ")
    }

    static func eventLine(name: String, screen: String) -> String {
        "analytics event=\(name) screen=\(screen)"
    }
}
