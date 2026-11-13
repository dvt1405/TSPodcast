import XCTest

/// App Store screenshots (not a regression test): Discover, show detail, Player and Search in the
/// language given by `STORE_LANG` / `STORE_LOCALE` (pass them as `TEST_RUNNER_STORE_LANG=de`
/// to `xcodebuild test`). Ads stay off (`-uitest-no-ads`) and the privacy sheet is pre-answered.
/// Screens are attached to the result bundle as `NN-name`; `iosApp/scripts/store-screenshots.sh`
/// extracts them and frames them with the brand template.
final class StoreScreenshotsUITests: TSPodcastUITestCase {
    @MainActor
    func testCaptureStoreScreens() {
        let env = ProcessInfo.processInfo.environment
        let language = env["STORE_LANG"] ?? "vi"
        let locale = env["STORE_LOCALE"] ?? "vi_VN"

        let app = XCUIApplication()
        app.launchArguments = [
            "-AppleLanguages", "(\(language))", "-AppleLocale", locale,
            "-uitest-skip-onboarding", "-uitest-consent=all", "-uitest-no-ads",
        ]
        app.launch()

        let firstShow = app.buttons["home.section.trending.item.0"]
        XCTAssertTrue(firstShow.waitForExistence(timeout: Self.networkTimeout), "trending not loaded")
        settle(5)
        attachScreenshot("01-home")

        firstShow.tap()
        XCTAssertTrue(element(app, "detail.root").waitForExistence(timeout: 10), "detail not shown")
        let episode = element(app, "detail.episode.0")
        XCTAssertTrue(episode.waitForExistence(timeout: Self.networkTimeout), "no episode")
        settle(4)
        attachScreenshot("02-detail")

        episode.tap()
        let player = element(app, "player.root")
        if !player.waitForExistence(timeout: 5) {
            let mini = app.buttons["miniplayer.open"]
            XCTAssertTrue(mini.waitForExistence(timeout: 10), "mini player not shown")
            mini.tap()
        }
        XCTAssertTrue(player.waitForExistence(timeout: 10), "player not shown")
        settle(6)
        attachScreenshot("03-player")
        let playPause = app.buttons["player.playPause"]
        if playPause.exists { playPause.tap() }
        let close = app.buttons["player.close"]
        if close.exists { close.tap() } else { player.swipeDown(velocity: .fast) }

        let tabs = app.tabBars.firstMatch.buttons.allElementsBoundByIndex.sorted { $0.frame.minX < $1.frame.minX }
        XCTAssertGreaterThanOrEqual(tabs.count, 3)
        tabs[tabs.count - 1].tap()
        XCTAssertTrue(element(app, "search.categories").waitForExistence(timeout: Self.networkTimeout), "search categories")
        settle(4)
        attachScreenshot("04-search")
    }

    /// Lets artwork and animations finish before a capture.
    @MainActor
    private func settle(_ seconds: TimeInterval) {
        _ = XCTWaiter.wait(for: [XCTestExpectation(description: "settle")], timeout: seconds)
    }
}
