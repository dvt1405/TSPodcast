import XCTest

/// M2-AC13, AC14 (error): with the API unreachable, Search shows "Không tải được nội dung" and
/// "Tải lại" instead of "no results" (Android showed a failure as the empty state), the category
/// chips still come from the bundled asset, and the category list has its own error + retry.
final class SearchErrorUITests: TSPodcastUITestCase {
    private let unreachable = ["TSP_UITEST_BASE_URL": "https://nonexistent.invalid/api/1.0/"]

    @MainActor
    func testUnreachableApiShowsErrorAndRetry() {
        let app = launchMain(environment: unreachable)
        openSearchTab(app)

        let arts = app.buttons["search.category.1"]
        arts.waitToExist(timeout: Self.networkTimeout, "categories from the bundled asset")
        XCTAssertEqual(arts.label, "Arts")

        typeSearch(app, "vietnam")

        XCTAssertEqual(waitForSearchOutcome(app), "error")
        XCTAssertEqual(element(app, "search.error").label, "Không tải được nội dung")
        let retry = app.buttons["search.retry"]
        XCTAssertTrue(retry.exists)
        XCTAssertEqual(retry.label, "Tải lại")
        XCTAssertFalse(element(app, "search.empty").exists, "a failure is not 'no results'")
        searchField(app).typeText("\n")
        attachScreenshot("M2-search-error")

        retry.tap()

        // The retry sends the request again; it fails again and the error comes back.
        _ = poll(timeout: 2) { self.element(app, "search.loading").exists }
        XCTAssertEqual(waitForSearchOutcome(app), "error", "error again after the retry")
        XCTAssertTrue(app.buttons["search.retry"].exists)
        XCTAssertFalse(element(app, "search.empty").exists)
        XCTAssertEqual(app.state, .runningForeground, "no crash")
    }

    @MainActor
    func testCategoryErrorShowsRetry() {
        let app = launchMain(environment: unreachable)
        openSearchTab(app)
        let arts = app.buttons["search.category.1"]
        arts.waitToExist(timeout: Self.networkTimeout, "categories from the bundled asset")
        arts.tap()

        element(app, "categoryPodcasts.root").waitToExist(timeout: 10)
        XCTAssertTrue(app.navigationBars["Arts"].waitForExistence(timeout: 5))
        let error = element(app, "categoryPodcasts.error")
        error.waitToExist(timeout: Self.networkTimeout, "category list error")
        XCTAssertEqual(error.label, "Không tải được nội dung")
        let retry = app.buttons["categoryPodcasts.retry"]
        XCTAssertEqual(retry.label, "Tải lại")
        attachScreenshot("M2-category-error")

        retry.tap()

        XCTAssertTrue(poll(timeout: Self.networkTimeout) {
            !self.element(app, "categoryPodcasts.loading").exists && self.element(app, "categoryPodcasts.error").exists
        }, "error again after the retry")
        XCTAssertEqual(app.state, .runningForeground)
    }
}
