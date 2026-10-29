import XCTest

/// M2-AC7: a failed episode load shows the "Rất tiếc!" alert with "Tải lại"/"Huỷ"; "Huỷ"
/// leaves an inline error with its own "Tải lại", never an endless shimmer (features-spec §3.5
/// [Sửa]). `-uitest-fail-detail-once` fails only the first load of the process.
///
/// That first load must be of a podcast with episodes (a trending feed can be explicit and
/// hidden by D6), so a first launch without the hook finds such a trending index.
final class PodcastDetailErrorUITests: TSPodcastUITestCase {
    /// Trending index whose Detail lists episodes, found by a launch without hooks.
    @MainActor
    private func trendingIndexWithEpisodes() -> Int {
        let app = launchMain()
        let opened = openDetailWithEpisodes(app)
        XCTAssertNotNil(opened, "no trending podcast with episodes")
        app.terminate()
        return opened?.index ?? 0
    }

    /// Relaunches with `-uitest-fail-detail-once` and opens trending item `index`.
    @MainActor
    private func launchFailingDetail(_ index: Int) -> XCUIApplication {
        let app = launchMain([.failDetailOnce])
        XCTAssertNotNil(openTrendingDetail(app, index), "trending item \(index)")
        return app
    }

    @MainActor
    private func waitForErrorAlert(_ app: XCUIApplication) -> XCUIElement {
        let alert = app.alerts["Rất tiếc!"]
        alert.waitToExist(timeout: 10, "error alert 'Rất tiếc!'")
        XCTAssertTrue(alert.staticTexts["Không tải được danh sách tập. Vui lòng thử lại."].exists, "alert message")
        XCTAssertTrue(alert.buttons["Tải lại"].exists)
        XCTAssertTrue(alert.buttons["Huỷ"].exists)
        return alert
    }

    @MainActor
    func testErrorAlertCancelThenInlineRetry() {
        let app = launchFailingDetail(trendingIndexWithEpisodes())

        let alert = waitForErrorAlert(app)
        attachScreenshot("M2-detail-error-alert")
        alert.buttons["Huỷ"].tap()

        XCTAssertTrue(poll(timeout: 5) { !alert.exists }, "alert closes")
        let message = element(app, "detail.episodes.error")
        message.waitToExist(timeout: 5, "inline error")
        XCTAssertEqual(message.label, "Không tải được danh sách tập")
        let retry = app.buttons["detail.episodes.retry"]
        XCTAssertTrue(retry.exists)
        XCTAssertEqual(retry.label, "Tải lại")
        _ = poll(timeout: 2) { false }
        XCTAssertFalse(element(app, "detail.episodes.loading").exists, "no endless shimmer")
        attachScreenshot("M2-detail-error-inline")

        retry.tap()
        XCTAssertEqual(waitForDetailContent(app), "episode")
    }

    @MainActor
    func testErrorAlertRetryLoadsEpisodes() {
        let app = launchFailingDetail(trendingIndexWithEpisodes())

        let alert = waitForErrorAlert(app)
        alert.buttons["Tải lại"].tap()

        XCTAssertTrue(poll(timeout: 5) { !alert.exists }, "alert closes")
        XCTAssertEqual(waitForDetailContent(app), "episode")
        XCTAssertFalse(element(app, "detail.episodes.error").exists)
    }
}
