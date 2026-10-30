import XCTest
@testable import TSPodcast

/// M4-AC18: push opt-in for `new_podcasts` only (features-spec §3.11, D4, M4 §4.4): nothing at
/// launch, the one-time prompt after the first favourite, topic (un)subscribe, denied path.
@MainActor
final class NotificationsModelTests: XCTestCase {
    private var system: FakeNotificationSystem!
    private var push: FakePushRegistrar!
    private var preferences: FakeNotificationPreferences!

    private func makeModel(optIn: Bool = false, promptShown: Bool = false) -> NotificationsModel {
        system = FakeNotificationSystem()
        push = FakePushRegistrar()
        preferences = FakeNotificationPreferences(optIn: optIn, promptShown: promptShown)
        return NotificationsModel(preferences: preferences, system: system, push: push)
    }

    func testLaunchNeverAsksForPermission() async {
        let model = makeModel()
        await model.start()
        await model.favouritesChanged(count: 0)

        XCTAssertEqual(system.authorizationRequests, 0)
        XCTAssertEqual(system.remoteRegistrations, 0)
        XCTAssertFalse(model.isEnabled)
        XCTAssertFalse(model.showPrompt)
        XCTAssertTrue(push.subscribed.isEmpty)
    }

    func testLaunchAfterOptInRegistersAgainWithoutAsking() async {
        let model = makeModel(optIn: true)
        system.status = .authorized
        await model.start()

        XCTAssertTrue(model.isEnabled)
        XCTAssertEqual(system.remoteRegistrations, 1, "new APNs token for FCM")
        XCTAssertEqual(system.authorizationRequests, 0)
    }

    func testOptInButDeniedInSystemSettingsShowsOff() async {
        let model = makeModel(optIn: true)
        system.status = .denied
        await model.start()

        XCTAssertFalse(model.isEnabled)
        XCTAssertEqual(system.remoteRegistrations, 0)
        XCTAssertEqual(system.authorizationRequests, 0)
    }

    func testFirstFavouriteShowsThePrompt() async {
        let model = makeModel()
        await model.favouritesChanged(count: 0)
        await model.favouritesChanged(count: 1)

        XCTAssertTrue(model.showPrompt)
        XCTAssertEqual(system.authorizationRequests, 0, "the in-app prompt comes before the iOS dialog")
    }

    func testExistingFavouritesAtLaunchDoNotPrompt() async {
        let model = makeModel()
        await model.favouritesChanged(count: 3)
        XCTAssertFalse(model.showPrompt)
    }

    func testSecondFavouriteDoesNotPrompt() async {
        let model = makeModel()
        await model.favouritesChanged(count: 1)
        await model.favouritesChanged(count: 2)
        XCTAssertFalse(model.showPrompt)
    }

    func testPromptShownBeforeDoesNotPromptAgain() async {
        let model = makeModel(promptShown: true)
        await model.favouritesChanged(count: 0)
        await model.favouritesChanged(count: 1)
        XCTAssertFalse(model.showPrompt)
    }

    func testNotNowSavesPromptShownAndAsksNothing() async {
        let model = makeModel()
        await model.favouritesChanged(count: 0)
        await model.favouritesChanged(count: 1)
        await model.declinePrompt()

        XCTAssertFalse(model.showPrompt)
        XCTAssertTrue(preferences.storedPromptShown)
        XCTAssertFalse(preferences.storedOptIn)
        XCTAssertEqual(system.authorizationRequests, 0)
        XCTAssertTrue(push.subscribed.isEmpty)

        // Removing every favourite and adding one again does not ask again.
        await model.favouritesChanged(count: 0)
        await model.favouritesChanged(count: 1)
        XCTAssertFalse(model.showPrompt)
    }

    func testTurnOnGrantedRegistersAndSubscribesOnce() async {
        let model = makeModel()
        await model.favouritesChanged(count: 0)
        await model.favouritesChanged(count: 1)
        await model.acceptPrompt()

        XCTAssertFalse(model.showPrompt)
        XCTAssertTrue(preferences.storedPromptShown)
        XCTAssertEqual(system.authorizationRequests, 1)
        XCTAssertEqual(system.remoteRegistrations, 1)
        XCTAssertEqual(push.subscribed, ["new_podcasts"])
        XCTAssertTrue(preferences.storedOptIn)
        XCTAssertTrue(model.isEnabled)
        XCTAssertFalse(model.showDeniedAlert)
    }

    func testTurnOnDeniedStaysOffAndOffersSettings() async {
        let model = makeModel()
        system.grants = false
        await model.favouritesChanged(count: 0)
        await model.favouritesChanged(count: 1)
        await model.acceptPrompt()

        XCTAssertFalse(preferences.storedOptIn)
        XCTAssertFalse(model.isEnabled)
        XCTAssertTrue(model.showDeniedAlert)
        XCTAssertEqual(system.remoteRegistrations, 0)
        XCTAssertTrue(push.subscribed.isEmpty)

        model.openSystemSettings()
        XCTAssertEqual(system.settingsOpened, 1)
        XCTAssertFalse(model.showDeniedAlert)
    }

    func testSettingsToggleOnSkipsTheInAppPrompt() async {
        let model = makeModel()
        await model.setEnabled(true)

        XCTAssertFalse(model.showPrompt)
        XCTAssertEqual(system.authorizationRequests, 1)
        XCTAssertEqual(push.subscribed, ["new_podcasts"])
        XCTAssertTrue(model.isEnabled)

        // Opted in from Settings: the first favourite no longer prompts.
        await model.favouritesChanged(count: 0)
        await model.favouritesChanged(count: 1)
        XCTAssertFalse(model.showPrompt)
    }

    func testSettingsToggleOffUnsubscribes() async {
        let model = makeModel(optIn: true)
        system.status = .authorized
        await model.start()
        await model.setEnabled(false)

        XCTAssertEqual(push.unsubscribed, ["new_podcasts"])
        XCTAssertFalse(preferences.storedOptIn)
        XCTAssertFalse(model.isEnabled)
        XCTAssertEqual(system.authorizationRequests, 0)
    }
}
