import XCTest

/// M3-AC8 … AC10, AC14, AC15: Detail → Player with the 60 s fixture tone (`TSP_UITEST_AUDIO_URL`),
/// the controls, Up next and the "Đang phát" mark. Episode lists come from PodcastIndex.
final class PlayerUITests: TSPodcastUITestCase {
    /// M3-AC8 (replaces M2 `testEpisodeTapHandsPlaylistToPlaybackSeam`).
    @MainActor
    func testEpisodeTapOpensPlayerAndPlays() {
        let app = launchWithAudio()
        guard let played = playEpisodeFromDetail(app) else { return }

        XCTAssertTrue(
            waitForProbe(app) { $0["index"] == "\(played.index)" && $0["count"] == "\(played.count)" && $0["playing"] == "1" },
            "probe: \(probe(app))"
        )
        let first = probeInt(app, "positionMs") ?? 0
        XCTAssertTrue(waitForProbe(app, timeout: 5) { Int($0["positionMs"] ?? "") ?? 0 >= first + 1_500 }, "position must advance: \(probe(app))")
        XCTAssertEqual(probe(app)["error"], "0")
        XCTAssertEqual(element(app, "player.title").label, played.episodeTitle)
        XCTAssertEqual(element(app, "player.subtitle").label, played.podcastTitle)
        XCTAssertFalse(app.buttons["player.more"].exists, "no More button (M3 §7 item 2)")
        attachScreenshot("M3-player")
    }

    /// M3-AC9: play/pause, slider seek, time labels.
    @MainActor
    func testPlayPauseSeekAndTimeLabels() {
        let app = launchWithAudio()
        guard playEpisodeFromDetail(app) != nil else { return }
        XCTAssertTrue(waitForProbe(app) { $0["playing"] == "1" && $0["durationMs"] != "0" }, "probe: \(probe(app))")

        let playPause = app.buttons["player.playPause"]
        XCTAssertEqual(playPause.label, "Tạm dừng")
        playPause.tap()
        XCTAssertTrue(waitForProbe(app, timeout: 5) { $0["playing"] == "0" }, "pause: \(probe(app))")
        XCTAssertTrue(poll(timeout: 3) { playPause.label == "Phát" }, playPause.label)
        playPause.tap()
        XCTAssertTrue(waitForProbe(app, timeout: 5) { $0["playing"] == "1" }, "play: \(probe(app))")
        XCTAssertTrue(poll(timeout: 3) { playPause.label == "Tạm dừng" }, playPause.label)

        app.sliders["player.slider"].adjust(toNormalizedSliderPosition: 0.5)
        XCTAssertTrue(
            waitForProbe(app, timeout: 5) { (25_000...35_000).contains(Int($0["positionMs"] ?? "") ?? -1) },
            "seek to the middle: \(probe(app))"
        )
        XCTAssertEqual(element(app, "player.duration").label, "1:00")
        let elapsed = element(app, "player.elapsed").label
        XCTAssertNotNil(elapsed.range(of: #"^\d+:\d{2}$"#, options: .regularExpression), elapsed)
    }

    /// M3-AC9: Previous/Next follow the playlist and are disabled at its ends.
    @MainActor
    func testPreviousNextFollowPlaylist() {
        let app = launchWithAudio()
        guard let played = playEpisodeFromDetail(app, preferredIndex: 0) else { return }
        XCTAssertTrue(waitForProbe(app) { $0["index"] == "0" }, "probe: \(probe(app))")
        XCTAssertFalse(app.buttons["player.previous"].isEnabled, "no previous on the first item")
        guard played.count > 1 else { return }

        app.buttons["player.next"].tap()
        XCTAssertTrue(waitForProbe(app, timeout: 5) { $0["index"] == "1" }, "next: \(probe(app))")
        XCTAssertTrue(app.buttons["player.previous"].isEnabled)
        app.buttons["player.previous"].tap()
        XCTAssertTrue(waitForProbe(app, timeout: 5) { $0["index"] == "0" }, "previous: \(probe(app))")

        // Last item through Up next, then Next is disabled.
        app.buttons["player.playlist"].tap()
        let last = played.count - 1
        XCTAssertTrue(revealInUpNext(app, last), "upNext.item.\(last)")
        app.buttons["upNext.item.\(last)"].tap()
        XCTAssertTrue(waitForProbe(app, timeout: 10) { $0["index"] == "\(last)" }, "last: \(probe(app))")
        XCTAssertTrue(poll(timeout: 3) { !app.buttons["player.next"].isEnabled }, "no next on the last item")
    }

    /// M3-AC9: the Favourite toggle.
    @MainActor
    func testFavouriteToggles() {
        let app = launchWithAudio()
        guard playEpisodeFromDetail(app) != nil else { return }
        let favourite = app.buttons["player.favourite"]
        favourite.waitToExist(timeout: 5)
        XCTAssertEqual(favourite.label, "Yêu thích")
        XCTAssertFalse(favourite.isSelected)
        XCTAssertEqual(probe(app)["fav"], "0")

        favourite.tap()
        XCTAssertTrue(waitForProbe(app, timeout: 3) { $0["fav"] == "1" }, "fav on: \(probe(app))")
        XCTAssertTrue(poll(timeout: 3) { favourite.isSelected })

        favourite.tap()
        XCTAssertTrue(waitForProbe(app, timeout: 3) { $0["fav"] == "0" }, "fav off: \(probe(app))")
        XCTAssertTrue(poll(timeout: 3) { !favourite.isSelected })
    }

    /// M3-AC10: Up next lists the whole queue, marks the current item and plays a tapped one.
    @MainActor
    func testUpNextListsQueueAndSelects() {
        let app = launchWithAudio()
        guard let played = playEpisodeFromDetail(app) else { return }
        XCTAssertTrue(waitForProbe(app) { $0["playing"] == "1" }, "probe: \(probe(app))")

        app.buttons["player.playlist"].tap()
        element(app, "upNext.root").waitToExist(timeout: 5, "Up next sheet")
        XCTAssertTrue(app.navigationBars["Tiếp theo"].waitForExistence(timeout: 3), "title 'Tiếp theo'")
        XCTAssertTrue(app.buttons["upNext.item.0"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["upNext.item.\(played.index)"].isSelected, "current item is selected")
        attachScreenshot("M3-up-next")
        let target = played.count > 2 ? 2 : (played.index == 0 ? min(1, played.count - 1) : 0)
        if played.count > 1 {
            XCTAssertTrue(revealInUpNext(app, played.count - 1), "last row upNext.item.\(played.count - 1)")
            XCTAssertFalse(app.buttons["upNext.item.\(played.count)"].exists, "no row past the queue")
            XCTAssertTrue(revealInUpNext(app, target, upwards: true), "upNext.item.\(target)")
        }
        app.buttons["upNext.item.\(target)"].tap()
        XCTAssertTrue(poll(timeout: 5) { !self.element(app, "upNext.root").exists }, "the sheet closes")
        XCTAssertTrue(waitForProbe(app, timeout: 10) { $0["index"] == "\(target)" && $0["playing"] == "1" }, "selected: \(probe(app))")
    }

    /// M3-AC14: Detail marks the episode the player holds.
    @MainActor
    func testDetailMarksNowPlayingEpisode() {
        let app = launchWithAudio()
        guard let played = playEpisodeFromDetail(app) else { return }
        XCTAssertTrue(waitForProbe(app) { $0["playing"] == "1" })
        closePlayer(app)

        let mark = element(app, "detail.episode.\(played.index).nowPlaying")
        XCTAssertTrue(mark.waitForExistence(timeout: 5), "mark on episode \(played.index)")
        XCTAssertEqual(mark.label, "Đang phát")
        for other in 0..<min(played.count, 4) where other != played.index {
            XCTAssertFalse(element(app, "detail.episode.\(other).nowPlaying").exists, "no mark on episode \(other)")
        }
        attachScreenshot("M3-detail-now-playing")
    }

    /// M3-AC15: the Player in dark mode (light is `M3-player`).
    @MainActor
    func testPlayerDarkAppearance() {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(vi)", "-AppleLocale", "vi_VN", "-AppleInterfaceStyle", "Dark", Hook.skipOnboarding.rawValue, Hook.playbackProbe.rawValue]
        app.launchEnvironment = ["TSP_UITEST_AUDIO_URL": Self.fixtureAudioURL]
        app.launch()
        element(app, "home.root").waitToExist(timeout: 10)
        guard playEpisodeFromDetail(app) != nil else { return }
        XCTAssertTrue(waitForProbe(app) { $0["playing"] == "1" })
        attachScreenshot("M3-player-dark")
    }
}

extension TSPodcastUITestCase {
    /// Swipes the Up next list until row `index` is hittable (at most `maxSwipes`).
    @MainActor
    func revealInUpNext(_ app: XCUIApplication, _ index: Int, upwards: Bool = false, maxSwipes: Int = 40) -> Bool {
        let row = app.buttons["upNext.item.\(index)"]
        let list = element(app, "upNext.root")
        for _ in 0..<maxSwipes {
            if row.exists, row.isHittable { return true }
            if upwards { list.swipeDown(velocity: .fast) } else { list.swipeUp(velocity: .fast) }
        }
        return row.exists && row.isHittable
    }
}
