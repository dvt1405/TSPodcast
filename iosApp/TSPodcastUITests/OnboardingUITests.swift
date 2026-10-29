import XCTest

/// M1-AC11, M1-AC13: the 4 onboarding pages and the persisted `OnboardingFinished` flag
/// (features-spec §3.1, Android `OnboardingScreen`).
final class OnboardingUITests: TSPodcastUITestCase {
    private let titles = [
        "Khám Phá Podcast Yêu Thích Của Bạn",
        "Nội Dung Dành Riêng Cho Bạn",
        "Nghe Mọi Lúc, Mọi Nơi",
        "Sắp Xếp Và Cập Nhật Liên Tục",
    ]

    @MainActor
    func testPagesAndContinueOnlyOnLastPage() {
        let app = launch([.reset])
        let continueButton = app.buttons["onboarding.continue"]

        waitForOnboardingPage(app, 0)
        XCTAssertTrue(app.otherElements["onboarding.page.0"].staticTexts[titles[0]].exists)
        XCTAssertFalse(continueButton.exists, "no 'Tiếp tục' on page 0")
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        attachScreenshot("onboarding-page-0")

        for page in 1...3 {
            swipeToOnboardingPage(app, page)
            XCTAssertTrue(app.otherElements["onboarding.page.\(page)"].staticTexts[titles[page]].exists, titles[page])
            if page < 3 {
                XCTAssertFalse(continueButton.exists, "no 'Tiếp tục' on page \(page)")
                attachScreenshot("onboarding-page-\(page)")
            }
        }

        continueButton.waitToExist(timeout: 5, "'Tiếp tục' must show on the last page")
        XCTAssertEqual(continueButton.label, "Tiếp tục")
        attachScreenshot("onboarding-page-3")

        continueButton.tap()

        app.scrollViews["category.grid"].waitToExist(timeout: 10, "'Tiếp tục' must open the category grid")
        XCTAssertTrue(app.staticTexts["Danh mục yêu thích của bạn"].exists)
        XCTAssertTrue(app.staticTexts["(Ít nhất 3 danh mục)"].exists)
        XCTAssertFalse(app.otherElements["onboarding.page.0"].exists)
    }

    @MainActor
    func testSwipingBackHidesContinue() {
        let app = launch([.reset])
        finishOnboardingPages(app)
        XCTAssertTrue(app.buttons["onboarding.continue"].waitForExistence(timeout: 5))

        app.swipeRight()
        waitForOnboardingPage(app, 2)

        XCTAssertFalse(app.buttons["onboarding.continue"].waitForExistence(timeout: 1))
    }

    @MainActor
    func testRelaunchAfterOnboardingOpensCategorySelection() {
        var app = launch([.reset])
        finishOnboarding(app)

        app.terminate()
        app = launch()

        app.scrollViews["category.grid"].waitToExist(timeout: 10, "OnboardingFinished must survive a relaunch")
        XCTAssertFalse(app.otherElements["onboarding.page.0"].exists)
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        attachScreenshot("relaunch-after-onboarding")
    }

    @MainActor
    private func finishOnboardingPages(_ app: XCUIApplication) {
        waitForOnboardingPage(app, 0)
        for page in 1...3 {
            swipeToOnboardingPage(app, page)
        }
    }
}
