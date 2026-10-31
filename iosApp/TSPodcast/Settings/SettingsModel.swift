import Foundation
import Observation
@preconcurrency import Shared

/// D6 flag of shared `AppPreferences` (`AllowExplicitContent`), behind a protocol for tests.
protocol ExplicitContentPreference: Sendable {
    func allowExplicitContent() async -> Bool
    func setAllowExplicitContent(_ allow: Bool) async
}

/// `ExplicitContentPreference` on shared `AppPreferences`. Home, Search and Detail read the flag
/// on their next load, so a change shows after a refresh.
struct SharedExplicitContentPreference: ExplicitContentPreference, @unchecked Sendable {
    let preferences: AppPreferences

    func allowExplicitContent() async -> Bool {
        (try? await preferences.allowExplicitContent().boolValue) ?? false
    }

    func setAllowExplicitContent(_ allow: Bool) async {
        do {
            try await preferences.saveAllowExplicitContent(allow: allow)
        } catch {
            AppLog.app.error("save_explicit_failed")
        }
    }
}

/// The minimal Settings screen (M4 §4.4, plan §7 item 10): new-podcast notifications, explicit
/// content (D6 "có toggle để bật lại"), privacy policy link (guideline 5.1.1) and app version.
@MainActor
@Observable
final class SettingsModel {
    static let privacyPolicyKey = "TSPrivacyPolicyURL"

    private(set) var allowExplicit = false
    let notifications: NotificationsModel
    /// `TSPrivacyPolicyURL` of Info.plist (`PRIVACY_POLICY_URL`); nil hides the row.
    let privacyURL: URL?
    /// "1.0.0 (1)": `CFBundleShortVersionString (CFBundleVersion)`.
    let version: String

    @ObservationIgnored private let explicit: any ExplicitContentPreference

    init(explicit: any ExplicitContentPreference, notifications: NotificationsModel, info: [String: Any]) {
        self.explicit = explicit
        self.notifications = notifications
        privacyURL = Self.privacyURL(from: info)
        version = Self.version(from: info)
    }

    func load() async {
        allowExplicit = await explicit.allowExplicitContent()
    }

    func setAllowExplicit(_ allow: Bool) async {
        allowExplicit = allow
        await explicit.setAllowExplicitContent(allow)
        AppLog.app.notice("settings_explicit allow=\(allow, privacy: .public)")
    }

    /// Goes through `NotificationsModel` (the prompt flow without the in-app alert).
    func setNotificationsEnabled(_ enabled: Bool) async {
        await notifications.setEnabled(enabled)
    }

    static func privacyURL(from info: [String: Any]) -> URL? {
        guard let text = (info[privacyPolicyKey] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !text.isEmpty,
              let url = URL(string: text),
              let scheme = url.scheme?.lowercased(), scheme == "https" || scheme == "http",
              url.host() != nil
        else { return nil }
        return url
    }

    static func version(from info: [String: Any]) -> String {
        let short = info["CFBundleShortVersionString"] as? String ?? "?"
        let build = info["CFBundleVersion"] as? String ?? "?"
        return "\(short) (\(build))"
    }
}
