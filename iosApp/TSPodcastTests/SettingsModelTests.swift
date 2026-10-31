import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M4-AC21: the minimal Settings screen (features-spec §3.11, README D6, guideline 5.1.1).
@MainActor
final class SettingsModelTests: XCTestCase {
    private func makeNotifications() -> (NotificationsModel, FakeNotificationSystem, FakePushRegistrar) {
        let system = FakeNotificationSystem()
        let push = FakePushRegistrar()
        let model = NotificationsModel(preferences: FakeNotificationPreferences(), system: system, push: push)
        return (model, system, push)
    }

    func testExplicitToggleWritesTheSharedFlag() async throws {
        let fixture = SharedGraphFixture()
        defer { fixture.close() }
        let explicit = SharedExplicitContentPreference(preferences: fixture.graph.appPreferences)
        let model = SettingsModel(explicit: explicit, notifications: makeNotifications().0, info: [:])

        await model.load()
        XCTAssertFalse(model.allowExplicit, "D6: filtered by default")

        await model.setAllowExplicit(true)
        XCTAssertTrue(model.allowExplicit)
        let stored = try await fixture.graph.appPreferences.allowExplicitContent().boolValue
        XCTAssertTrue(stored, "AllowExplicitContent, read by Home/Search/Detail on their next load")

        let reopened = SettingsModel(explicit: explicit, notifications: makeNotifications().0, info: [:])
        await reopened.load()
        XCTAssertTrue(reopened.allowExplicit)

        await reopened.setAllowExplicit(false)
        let cleared = try await fixture.graph.appPreferences.allowExplicitContent().boolValue
        XCTAssertFalse(cleared)
    }

    func testNotificationToggleGoesThroughNotificationsModel() async {
        let (notifications, system, push) = makeNotifications()
        let model = SettingsModel(explicit: FakeExplicit(), notifications: notifications, info: [:])

        await model.setNotificationsEnabled(true)
        XCTAssertEqual(system.authorizationRequests, 1)
        XCTAssertEqual(push.subscribed, ["new_podcasts"])
        XCTAssertTrue(model.notifications.isEnabled)

        await model.setNotificationsEnabled(false)
        XCTAssertEqual(push.unsubscribed, ["new_podcasts"])
        XCTAssertFalse(model.notifications.isEnabled)
    }

    func testPrivacyRowOnlyWithAValidURL() {
        for value in [nil, "", "   ", "not a url", "ftp://example.com/p", "https://"] as [String?] {
            let info: [String: Any] = value.map { [SettingsModel.privacyPolicyKey: $0] } ?? [:]
            XCTAssertNil(SettingsModel.privacyURL(from: info), value ?? "missing")
        }
        XCTAssertEqual(
            SettingsModel.privacyURL(from: [SettingsModel.privacyPolicyKey: "https://example.com/privacy"])?.absoluteString,
            "https://example.com/privacy"
        )
    }

    /// The app's own Info.plist: `PRIVACY_POLICY_URL` is empty until the owner sets it (handoff),
    /// so the row is hidden; the version reads "1.0.0 (1)".
    func testAppInfoPlist() {
        let info = Bundle.main.infoDictionary ?? [:]
        XCTAssertNotNil(info[SettingsModel.privacyPolicyKey], "TSPrivacyPolicyURL is declared")
        XCTAssertNil(SettingsModel.privacyURL(from: info))
        XCTAssertEqual(SettingsModel.version(from: info), "1.0.0 (1)")
    }

    /// The "Ngôn ngữ" row names the app language, first letter capitalised.
    func testLanguageName() {
        XCTAssertEqual(SettingsModel.languageName(of: "vi", in: Locale(identifier: "vi")), "Tiếng Việt")
        XCTAssertEqual(SettingsModel.languageName(of: "en", in: Locale(identifier: "en")), "English")
        XCTAssertEqual(SettingsModel.languageName(of: "fr", in: Locale(identifier: "fr")), "Français")
        XCTAssertEqual(SettingsModel.languageName(of: "pt-BR", in: Locale(identifier: "pt-BR")), "Português (Brasil)")
        XCTAssertFalse(SettingsModel.languageName(of: Bundle.main.preferredLocalizations.first ?? "vi").isEmpty)
    }

    func testVersionFormat() {
        XCTAssertEqual(SettingsModel.version(from: ["CFBundleShortVersionString": "2.3.4", "CFBundleVersion": "56"]), "2.3.4 (56)")
    }
}

private struct FakeExplicit: ExplicitContentPreference {
    func allowExplicitContent() async -> Bool { false }
    func setAllowExplicitContent(_ allow: Bool) async {}
}
