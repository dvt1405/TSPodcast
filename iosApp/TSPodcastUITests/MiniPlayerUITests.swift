import XCTest

/// M3-AC11: the real MiniPlayer after closing the Player.
final class MiniPlayerUITests: TSPodcastUITestCase {
    @MainActor
    func testMiniPlayerAfterClosingPlayer() {
        let app = launchWithAudio()
        guard let played = playEpisodeFromDetail(app) else { return }
        XCTAssertTrue(waitForProbe(app) { $0["playing"] == "1" }, "probe: \(probe(app))")
        XCTAssertFalse(element(app, "miniplayer.slot").exists, "no MiniPlayer while the Player is open")

        closePlayer(app)
        let slot = element(app, "miniplayer.slot")
        slot.waitToExist(timeout: 5, "MiniPlayer after closing the Player")
        // The open button reads "<episode title>, <podcast name>" (title line, subtitle line).
        let open = app.buttons["miniplayer.open"]
        XCTAssertEqual(open.label, "\(played.episodeTitle), \(played.podcastTitle)")
        XCTAssertTrue(element(app, "miniplayer.progress").exists, "thin progress")
        attachScreenshot("M3-miniplayer")

        let playPause = app.buttons["miniplayer.playPause"]
        XCTAssertEqual(playPause.label, "Tạm dừng")
        playPause.tap()
        XCTAssertTrue(waitForProbe(app, timeout: 5) { $0["playing"] == "0" }, "pause: \(probe(app))")
        XCTAssertTrue(poll(timeout: 3) { playPause.label == "Phát" })
        settle()
        playPause.tap()
        XCTAssertTrue(waitForProbe(app, timeout: 5) { $0["playing"] == "1" }, "play: \(probe(app))")
        XCTAssertTrue(poll(timeout: 3) { playPause.label == "Tạm dừng" })

        settle()
        open.tap()
        XCTAssertTrue(element(app, "player.root").waitForExistence(timeout: 5), "tap opens the Player")
        XCTAssertTrue(poll(timeout: 3) { !playPause.exists || playPause.frame.isEmpty || !playPause.isHittable }, "MiniPlayer hidden under the Player")
        XCTAssertEqual(element(app, "player.title").label, played.episodeTitle)
    }

    /// The tab bar accessory drops a tap that follows another one too closely (seen on
    /// iOS 26.3): let it settle between taps.
    @MainActor
    private func settle() {
        RunLoop.current.run(until: Date().addingTimeInterval(1))
    }
}
