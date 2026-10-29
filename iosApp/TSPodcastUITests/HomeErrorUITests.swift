import XCTest

/// M1-AC17: per-section loading, error and "Tải lại" (features-spec §3.4 [Sửa]; Android shows a
/// shimmer forever instead).
final class HomeErrorUITests: TSPodcastUITestCase {
    private let unreachable = ["TSP_UITEST_BASE_URL": "https://nonexistent.invalid/api/1.0/"]

    @MainActor
    func testUnreachableApiShowsErrorAndRetryPerSection() {
        let app = launchMain(environment: unreachable)

        for (id, _) in Self.homeSections {
            let header = app.staticTexts["home.section.\(id)"]
            XCTAssertTrue(scrollHome(app, to: header), "header \(id) not reached")
            XCTAssertEqual(waitForSectionContent(app, id), "error", "\(id) must fail")
            let retry = app.buttons["home.section.\(id).retry"]
            XCTAssertTrue(scrollHome(app, to: retry, maxDrags: 3), "\(id) retry not visible")
            XCTAssertEqual(element(app, "home.section.\(id).error").label, "Không tải được nội dung")
            XCTAssertEqual(retry.label, "Tải lại")
            if id == "live" {
                attachScreenshot("home-errors-top")
            }
        }

        let footer = scrollHomeToBottom(app)
        attachScreenshot("home-errors-bottom")
        XCTAssertTrue(isVisibleInHome(app, footer), "footer still shown: \(footer.exists) \(footer.frame) tab \(app.tabBars.firstMatch.frame) nav \(app.navigationBars.firstMatch.frame)")
        XCTAssertEqual(footer.label, "Dữ liệu podcast cung cấp bởi Podcast Index")

        // Retry live: back to the top, then down to the live section.
        for _ in 0..<15 where !element(app, "home.section.trending").exists {
            element(app, "home.list").swipeDown(velocity: .fast)
        }
        let liveRetry = app.buttons["home.section.live.retry"]
        XCTAssertTrue(scrollHome(app, to: liveRetry), "live retry not reached")
        liveRetry.tap()

        XCTAssertEqual(waitForSectionContent(app, "live"), "error", "live fails again after the retry")
        XCTAssertTrue(app.buttons["home.section.live.retry"].exists)
        XCTAssertEqual(app.state, .runningForeground, "no crash")
        attachScreenshot("home-live-retried")
    }

    @MainActor
    func testLoadingPlaceholdersShown() {
        let app = launch([.skipOnboarding, .slowNetwork])

        // Every call waits first (`HomeFeedDataSource.slowNetworkDelay`): each section shimmers.
        for id in ["trending", "music"] {
            XCTAssertTrue(element(app, "home.section.\(id).loading").waitForExistence(timeout: 2), "\(id) loading")
        }
        attachScreenshot("home-loading")
        // While loading, Home is short: one or two flings reach the lower placeholders.
        for id in ["live", "recent", "forYou"] {
            let loading = element(app, "home.section.\(id).loading")
            for _ in 0..<3 where !loading.exists {
                element(app, "home.list").swipeUp(velocity: .fast)
            }
            XCTAssertTrue(loading.exists, "\(id) loading")
        }
        attachScreenshot("home-loading-lower")

        // `generation=1`: every section finished its first load, within 30 s of the delay.
        let list = element(app, "home.list")
        XCTAssertTrue(waitForValue(list, "generation=1", timeout: Self.networkTimeout), "Home still loading")
        let loadingQuery = app.descendants(matching: .any).matching(NSPredicate(format: "identifier ENDSWITH '.loading'"))
        XCTAssertEqual(loadingQuery.count, 0, "no shimmer left at the bottom")
        for _ in 0..<5 where !element(app, "home.section.trending").exists {
            list.swipeDown(velocity: .fast)
        }
        XCTAssertNotNil(waitForSectionContent(app, "trending", timeout: 5))
        XCTAssertEqual(loadingQuery.count, 0, "no shimmer left at the top")
    }
}
