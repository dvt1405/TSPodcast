import XCTest

/// M3-AC12: Home Live → Player with the whole Live list.
final class LivePlayerUITests: TSPodcastUITestCase {
    /// Waits for the Live section, taps item `preferred` (or 0) and returns its index and the
    /// section size (`count=N` of `home.live.pager`, Debug).
    @MainActor
    private func playLive(_ app: XCUIApplication, preferred: Int = 1) -> (index: Int, count: Int)? {
        guard waitForSectionContent(app, "live") == "item" else {
            XCTFail("Live section has no item")
            return nil
        }
        let pager = element(app, "home.live.pager")
        guard let value = pager.value as? String, value.hasPrefix("count="), let count = Int(value.dropFirst(6)) else {
            XCTFail("home.live.pager value: \(String(describing: pager.value))")
            return nil
        }
        let index = count > preferred ? preferred : 0
        let item = app.buttons["home.section.live.item.\(index)"]
        XCTAssertTrue(scrollHome(app, to: item), "live item \(index) not visible")
        item.tap()
        XCTAssertTrue(element(app, "player.root").waitForExistence(timeout: 10), "Live tap opens the Player")
        return (index, count)
    }

    @MainActor
    func testLiveItemOpensPlayerWithWholeLiveList() {
        let app = launchWithAudio()
        guard let played = playLive(app) else { return }
        XCTAssertTrue(
            waitForProbe(app) { $0["index"] == "\(played.index)" && $0["count"] == "\(played.count)" && $0["playing"] == "1" },
            "probe: \(probe(app)), expected index \(played.index) count \(played.count)"
        )
        attachScreenshot("M3-live-player")
    }

    @MainActor
    func testForcedLiveShowsLiveBadgeWithoutSeek() {
        let app = launchWithAudio([.forceLive])
        guard playLive(app) != nil else { return }
        XCTAssertTrue(waitForProbe(app) { $0["live"] == "1" && $0["playing"] == "1" }, "probe: \(probe(app))")
        let badge = element(app, "player.live")
        XCTAssertTrue(badge.waitForExistence(timeout: 5), "LIVE badge")
        XCTAssertEqual(badge.label, "LIVE")
        XCTAssertFalse(app.sliders["player.slider"].exists, "no seek bar on live")
        XCTAssertFalse(element(app, "player.duration").exists, "no duration on live")
        attachScreenshot("M3-live-badge")
    }
}
