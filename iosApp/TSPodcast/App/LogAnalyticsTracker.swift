import Foundation
@preconcurrency import Shared

/// `AnalyticsTracker` that writes each call to OSLog (category `Analytics`) until a Firebase iOS
/// app exists. Lines look like `analytics screen=Onboarding CurrentPage=2` and
/// `analytics event=OnboardingFinished screen=Onboarding`. No device identifier, ever.
final class LogAnalyticsTracker: NSObject, AnalyticsTracker, Sendable {
    func trackScreen(screen: String, params: [String: String]) {
        let line = Self.screenLine(screen: screen, params: params)
        AppLog.analytics.notice("\(line, privacy: .public)")
    }

    func trackEvent(name: String, screen: String?, params: [String: String]) {
        let line = Self.eventLine(name: name, screen: screen, params: params)
        AppLog.analytics.notice("\(line, privacy: .public)")
    }

    /// Parameters are sorted by name so a line is stable and easy to grep.
    static func screenLine(screen: String, params: [String: String]) -> String {
        (["analytics screen=\(screen)"] + sortedPairs(params)).joined(separator: " ")
    }

    /// `screen=` is left out when no screen is known (a push handled outside any screen).
    static func eventLine(name: String, screen: String?, params: [String: String] = [:]) -> String {
        var parts = ["analytics event=\(name)"]
        if let screen { parts.append("screen=\(screen)") }
        return (parts + sortedPairs(params)).joined(separator: " ")
    }

    private static func sortedPairs(_ params: [String: String]) -> [String] {
        params.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }
    }
}
