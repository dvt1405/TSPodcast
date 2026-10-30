import XCTest

/// M3-AC13: an item that cannot play shows the error and "Tải lại" (M3 §7 item 3).
final class PlayerErrorUITests: TSPodcastUITestCase {
    @MainActor
    func testUnplayableItemShowsErrorAndRetry() {
        let app = launchWithAudio(audioURL: Self.missingAudioURL)
        guard playEpisodeFromDetail(app) != nil else { return }

        let error = element(app, "player.error")
        XCTAssertTrue(error.waitForExistence(timeout: 15), "error message, probe: \(probe(app))")
        XCTAssertEqual(error.label, "Không phát được tập này")
        let retry = app.buttons["player.retry"]
        XCTAssertEqual(retry.label, "Tải lại")
        XCTAssertTrue(waitForProbe(app, timeout: 3) { $0["error"] == "1" && $0["playing"] == "0" }, "probe: \(probe(app))")
        attachScreenshot("M3-player-error")

        retry.tap()
        XCTAssertTrue(waitForProbe(app, timeout: 15) { $0["error"] == "1" && $0["playing"] == "0" }, "still failing: \(probe(app))")
        XCTAssertTrue(error.waitForExistence(timeout: 5))
        XCTAssertTrue(element(app, "player.root").exists, "no crash, still on the Player")
    }
}
