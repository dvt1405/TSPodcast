import XCTest

/// Launch routing (features-spec §1) through the Debug hooks; skeleton of the M1 UI suite.
final class LaunchSmokeUITests: TSPodcastUITestCase {
    @MainActor
    func testLaunchWithResetShowsOnboarding() {
        let app = launch([.reset])

        let firstPage = app.otherElements["onboarding.page.0"]
        firstPage.waitToExist(timeout: 10, "a reset store must start on onboarding")
        XCTAssertTrue(app.staticTexts["Khám Phá Podcast Yêu Thích Của Bạn"].exists)
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        attachScreenshot("launch-reset-onboarding")
    }

    @MainActor
    func testSkipOnboardingOpensMainOnDiscover() {
        let app = launch([.skipOnboarding])

        app.scrollViews["home.root"].waitToExist(timeout: 10, "skip-onboarding must open Main")
        XCTAssertFalse(app.otherElements["onboarding.page.0"].exists)
        let discover = app.tabBars.buttons["Khám phá"]
        XCTAssertTrue(discover.exists)
        XCTAssertTrue(discover.isSelected)
        attachScreenshot("launch-skip-onboarding-main")
    }
}
