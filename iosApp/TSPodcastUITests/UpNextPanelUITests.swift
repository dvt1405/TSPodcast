import XCTest

/// The "Tiếp theo" sheet of the Player (M5 §3.1, §4) on a 30-item fixture queue
/// (`-uitest-queue=30`, no network): the peek under the controls, opening and closing by drag,
/// tap, the playlist button and a row, scrolling the open list and pulling it down from its top,
/// rapid toggles, landscape, the speed/sleep sheets over it and reopening the Player. Every step attaches a screenshot; the `testHitches…` tests measure scroll and panel
/// animation hitches (`XCTOSSignpostMetric`).
final class UpNextPanelUITests: TSPodcastUITestCase {
    static let queueCount = 30
    /// A real fling for the panel's 1000 pt/s rule. `XCUIGestureVelocity.fast` is only 750 and
    /// a 120 pt drag at that speed is (correctly) a short drag that snaps back.
    static let fling = XCUIGestureVelocity(4000)

    // MARK: - Launch and helpers

    /// Main with the fixture queue playing and the Player open (consent saved so its sheet does
    /// not cover the Player).
    @MainActor
    @discardableResult
    func launchPlayerWithLongQueue(probe: Bool = true) -> XCUIApplication {
        let app = XCUIApplication()
        var arguments = ["-AppleLanguages", "(vi)", "-AppleLocale", "vi_VN", Hook.skipOnboarding.rawValue, "-uitest-consent=all", "-uitest-queue=\(Self.queueCount)"]
        if probe { arguments.append(Hook.playbackProbe.rawValue) }
        app.launchArguments = arguments
        // No TSP_UITEST_AUDIO_URL: on a device the app cannot read the runner's tone-60s.m4a
        // (the Player would show its load error); the fixture items carry an app-written WAV.
        app.launch()
        XCTAssertTrue(element(app, "player.root").waitForExistence(timeout: 20), "the fixture queue must open the Player")
        if probe {
            XCTAssertTrue(
                waitForProbe(app, timeout: 10) { $0["count"] == "\(Self.queueCount)" && $0["index"] == "0" && $0["playing"] == "1" && $0["error"] == "0" },
                "probe: \(self.probe(app))"
            )
        }
        peek(app).waitToExist(timeout: 5, "peek under the controls")
        _ = settledFrame(peek(app))
        return app
    }

    /// Notification banners of other apps (the owner's phone gets many) cover the top of the
    /// screen, where the open header sits: swipe them away before touching the panel.
    @MainActor
    func dismissBanners() {
        let banner = XCUIApplication(bundleIdentifier: "com.apple.springboard").otherElements["NotificationShortLookView"]
        for _ in 0..<3 where banner.exists {
            banner.swipeUp()
            _ = poll(timeout: 2) { !banner.exists }
        }
    }

    /// Taps the grabber/title band of the header (on the header whether open or collapsed).
    @MainActor
    func tapHeader(_ app: XCUIApplication) {
        dismissBanners()
        peek(app).coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).withOffset(CGVector(dx: 0, dy: 20)).tap()
    }

    @MainActor
    func peek(_ app: XCUIApplication) -> XCUIElement {
        element(app, "upNext.peek")
    }

    @MainActor
    func list(_ app: XCUIApplication) -> XCUIElement {
        element(app, "upNext.root")
    }

    /// Frame of `element` once it stops moving (two equal samples 0.15 s apart, at most `timeout`).
    @MainActor
    @discardableResult
    func settledFrame(_ element: XCUIElement, timeout: TimeInterval = 4) -> CGRect {
        var last = CGRect.null
        var frame = CGRect.null
        _ = poll(timeout: timeout) {
            guard element.exists else { return false }
            frame = element.frame
            defer { last = frame }
            return frame == last
        }
        RunLoop.current.run(until: Date().addingTimeInterval(0.15))
        return element.exists ? element.frame : frame
    }

    /// Lowest edge of the transport row and the speed/timer row.
    @MainActor
    func controlsBottom(_ app: XCUIApplication) -> CGFloat {
        ["player.playPause", "player.favourite", "player.skipBack", "player.skipForward", "player.playlist", "player.speed", "player.sleepTimer"]
            .map { app.buttons[$0] }
            .filter(\.exists)
            .map(\.frame.maxY)
            .max() ?? 0
    }

    /// Lowest text of the header (the title, plus the peek rows while collapsed). The header
    /// element's own frame can reach the panel bottom (see `testPeekSitsUnderControls`).
    @MainActor
    func headerContentBottom(_ app: XCUIApplication) -> CGFloat {
        peek(app).staticTexts.allElementsBoundByIndex.map(\.frame.maxY).max() ?? .infinity
    }

    /// Bottom of the close row (where the open panel's top must sit).
    @MainActor
    func closeRowBottom(_ app: XCUIApplication) -> CGFloat {
        app.buttons["player.close"].frame.maxY
    }

    /// Open: the list exists and the panel top sits just below the close row.
    @MainActor
    func isExpanded(_ app: XCUIApplication) -> Bool {
        guard list(app).exists, peek(app).exists else { return false }
        let top = peek(app).frame.minY
        return abs(top - closeRowBottom(app)) <= 6
    }

    /// Collapsed: no list and the peek at `collapsedTop` (± 2 pt) under the controls.
    @MainActor
    func isCollapsed(_ app: XCUIApplication, at collapsedTop: CGFloat) -> Bool {
        guard !list(app).exists, peek(app).exists else { return false }
        return abs(peek(app).frame.minY - collapsedTop) <= 2
    }

    @MainActor
    func waitExpanded(_ app: XCUIApplication, _ message: String, file: StaticString = #filePath, line: UInt = #line) {
        let ok = poll(timeout: 4) { self.isExpanded(app) }
        _ = settledFrame(peek(app))
        XCTAssertTrue(ok && isExpanded(app), "\(message): expected open, peek \(peek(app).frame), close row bottom \(closeRowBottom(app)), list \(list(app).exists)", file: file, line: line)
    }

    @MainActor
    func waitCollapsed(_ app: XCUIApplication, at collapsedTop: CGFloat, _ message: String, file: StaticString = #filePath, line: UInt = #line) {
        let ok = poll(timeout: 4) { self.isCollapsed(app, at: collapsedTop) }
        _ = settledFrame(peek(app))
        XCTAssertTrue(ok && isCollapsed(app, at: collapsedTop), "\(message): expected the peek at y=\(collapsedTop), got \(peek(app).frame), list \(list(app).exists)", file: file, line: line)
    }

    /// Drags from the middle of the peek header by `dy` points at `velocity` pt/s, then holds
    /// `hold` s (a hold brings the release velocity to ~0, so only the distance decides).
    @MainActor
    func dragHeader(_ app: XCUIApplication, by dy: CGFloat, velocity: XCUIGestureVelocity = XCUIGestureVelocity(600), hold: TimeInterval = 0.4) {
        dismissBanners()
        let header = peek(app)
        // The grabber + title band: on the header whether collapsed or open.
        let start = header.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).withOffset(CGVector(dx: 0, dy: 20))
        let end = start.withOffset(CGVector(dx: 0, dy: dy))
        start.press(forDuration: 0.05, thenDragTo: end, withVelocity: velocity, thenHoldForDuration: hold)
    }

    // MARK: - Tests

    /// Peek visible, inside the screen, under (never over) the transport and speed/timer rows.
    @MainActor
    func testPeekSitsUnderControls() {
        let app = launchPlayerWithLongQueue()
        let peekFrame = settledFrame(peek(app))
        attachScreenshot("01-collapsed-peek")
        XCTAssertFalse(list(app).exists, "upNext.root only while open")
        XCTAssertTrue(peek(app).isHittable, "peek hittable")
        XCTAssertGreaterThanOrEqual(peekFrame.minY + 1, controlsBottom(app), "peek \(peekFrame) overlaps the controls (bottom \(controlsBottom(app)))")
        // The texts of the peek, not the header element: collapsed, its accessibility frame runs to
        // the bottom of the panel (835 pt on Tun, below the screen) although it is laid out 176 pt
        // tall; open it is right. Cause not found (M5 progress notes).
        let contentBottom = headerContentBottom(app)
        XCTAssertLessThanOrEqual(contentBottom, app.frame.maxY + 1, "peek content (bottom \(contentBottom)) runs off screen \(app.frame)")
        XCTAssertGreaterThan(peekFrame.height, 60, "peek shows title + rows: \(peekFrame)")
        XCTAssertTrue(app.buttons["player.playPause"].isHittable, "play/pause stays tappable")
        XCTAssertTrue(app.buttons["player.playlist"].isHittable, "playlist stays tappable")
        XCTAssertTrue((peek(app).value as? String)?.contains("Tập 1") ?? false, "peek value: \(String(describing: peek(app).value))")
    }

    /// Swipe up on the peek opens (top below the close row); a slow short drag snaps back;
    /// swipe down on the header closes.
    @MainActor
    func testDragOpensAndCloses() {
        let app = launchPlayerWithLongQueue()
        let collapsedTop = settledFrame(peek(app)).minY
        let travel = collapsedTop - closeRowBottom(app)
        XCTAssertGreaterThan(travel, 200, "travel \(travel)")

        // 25 % up, slow, held: below the 40 % threshold → back to the peek.
        dragHeader(app, by: -travel * 0.25)
        attachScreenshot("02a-after-short-drag-up")
        waitCollapsed(app, at: collapsedTop, "a 25 % drag must snap back")

        // 60 % up, slow, held: past 40 % → open.
        dragHeader(app, by: -travel * 0.6)
        attachScreenshot("02b-after-60pct-drag-up")
        waitExpanded(app, "a 60 % drag up must open")
        attachScreenshot("02c-expanded")
        XCTAssertTrue(app.buttons["player.close"].isHittable, "close stays tappable while open")

        // 10 % down, held: below 20 % → stays open.
        dragHeader(app, by: travel * 0.1)
        waitExpanded(app, "a 10 % drag down must keep it open")
        attachScreenshot("02d-after-short-drag-down")

        // 40 % down, held: past 20 % → closed.
        dragHeader(app, by: travel * 0.4)
        attachScreenshot("02e-after-40pct-drag-down")
        waitCollapsed(app, at: collapsedTop, "a 40 % drag down must close")

        // Fast flings: a short fast swipe follows its direction.
        dragHeader(app, by: -120, velocity: Self.fling, hold: 0)
        waitExpanded(app, "a fast fling up must open")
        attachScreenshot("02f-after-fling-up")
        dragHeader(app, by: 120, velocity: Self.fling, hold: 0)
        waitCollapsed(app, at: collapsedTop, "a fast fling down must close")
        attachScreenshot("02g-after-fling-down")
    }

    /// The open list scrolls to its end and back several times without moving the panel.
    @MainActor
    func testOpenListScrolls() {
        let app = launchPlayerWithLongQueue()
        app.buttons["player.playlist"].tap()
        waitExpanded(app, "playlist must open")
        attachScreenshot("03a-open-top")
        let panelTop = peek(app).frame.minY
        let firstRow = app.buttons["upNext.item.0"]
        let lastRow = app.buttons["upNext.item.\(Self.queueCount - 1)"]
        XCTAssertTrue(firstRow.waitForExistence(timeout: 3), "row 0")
        XCTAssertTrue(firstRow.isSelected, "row 0 is the playing one")

        for round in 1...3 {
            XCTAssertTrue(revealInUpNext(app, Self.queueCount - 1, maxSwipes: 12), "round \(round): last row reachable")
            attachScreenshot("03b-round\(round)-bottom")
            XCTAssertLessThanOrEqual(lastRow.frame.maxY, app.frame.maxY, "last row above the screen bottom: \(lastRow.frame)")
            XCTAssertTrue(revealInUpNext(app, 0, upwards: true, maxSwipes: 12), "round \(round): first row back")
            attachScreenshot("03c-round\(round)-top")
            XCTAssertEqual(peek(app).frame.minY, panelTop, accuracy: 1, "scrolling must not move the panel")
            XCTAssertTrue(list(app).exists, "still open after scrolling")
        }
        // A list row frame must start under the header, not behind it.
        XCTAssertGreaterThanOrEqual(firstRow.frame.minY + 1, headerContentBottom(app), "row 0 \(firstRow.frame) under the header title (bottom \(headerContentBottom(app)))")
    }

    /// Tap on the header toggles; the playlist button opens.
    @MainActor
    func testTapHeaderTogglesAndPlaylistOpens() {
        let app = launchPlayerWithLongQueue()
        let collapsedTop = settledFrame(peek(app)).minY
        tapHeader(app)
        waitExpanded(app, "tap on the peek must open")
        attachScreenshot("04a-tap-opened")
        tapHeader(app)
        waitCollapsed(app, at: collapsedTop, "tap on the open header must close")
        attachScreenshot("04b-tap-closed")
        app.buttons["player.playlist"].tap()
        waitExpanded(app, "playlist must open")
        attachScreenshot("04c-playlist-opened")
        tapHeader(app)
        waitCollapsed(app, at: collapsedTop, "tap closes after playlist")
    }

    /// A tapped row plays and the panel goes back to the peek, which then shows that row.
    @MainActor
    func testTapRowPlaysAndCollapses() {
        let app = launchPlayerWithLongQueue()
        let collapsedTop = settledFrame(peek(app)).minY
        app.buttons["player.playlist"].tap()
        waitExpanded(app, "playlist must open")
        XCTAssertTrue(revealInUpNext(app, 12, maxSwipes: 6), "row 12")
        attachScreenshot("05a-before-row-tap")
        app.buttons["upNext.item.12"].tap()
        XCTAssertTrue(waitForProbe(app, timeout: 10) { $0["index"] == "12" }, "row 12 plays: \(probe(app))")
        waitCollapsed(app, at: collapsedTop, "a row tap must close")
        attachScreenshot("05b-after-row-tap")
        XCTAssertTrue((peek(app).value as? String)?.contains("Tập 13") ?? false, "peek shows the new item: \(String(describing: peek(app).value))")
        XCTAssertEqual(element(app, "player.title").label.hasPrefix("Tập 13"), true, element(app, "player.title").label)

        // Opening again shows the playing row 12 without scrolling, selected.
        app.buttons["player.playlist"].tap()
        waitExpanded(app, "reopen")
        attachScreenshot("05c-reopened-after-row-tap")
        let row = app.buttons["upNext.item.12"]
        XCTAssertTrue(row.exists && row.isHittable, "the open list must show the playing row 12: \(row.exists ? row.frame : .null)")
        XCTAssertTrue(revealInUpNext(app, 12, maxSwipes: 6), "row 12 again")
        XCTAssertTrue(row.isSelected, "row 12 selected")
    }

    /// Quick repeated toggles (taps, playlist, flings) end in a consistent state.
    @MainActor
    func testRapidTogglesEndConsistent() {
        let app = launchPlayerWithLongQueue()
        let collapsedTop = settledFrame(peek(app)).minY
        let header = peek(app)

        // 10 quick taps (5 open/close pairs) on the header wherever it is, no wait → collapsed.
        // Banners are swiped away before each tap: one over the open header took a tap on Tun.
        var trace: [String] = []
        for _ in 0..<10 {
            let before = peek(app).frame.minY
            tapHeader(app)
            trace.append("\(Int(before))→\(Int(peek(app).frame.minY))")
        }
        print("quick taps: \(trace)")
        attachScreenshot("06a-after-10-quick-taps")
        waitCollapsed(app, at: collapsedTop, "10 quick taps end closed (header y → state after each tap: \(trace))")
        attachScreenshot("06b-settled-after-10-taps")

        // 5 fling pairs → collapsed (each fling starts on the header once it settled).
        for _ in 0..<5 {
            _ = settledFrame(header, timeout: 1.5)
            dragHeader(app, by: -120, velocity: Self.fling, hold: 0)
            _ = settledFrame(header, timeout: 1.5)
            dragHeader(app, by: 120, velocity: Self.fling, hold: 0)
        }
        attachScreenshot("06c-after-5-fling-pairs")
        waitCollapsed(app, at: collapsedTop, "5 fling pairs end closed")

        // Odd count → open, and it still works afterwards.
        for _ in 0..<5 { tapHeader(app) }
        waitExpanded(app, "5 quick taps end open")
        attachScreenshot("06d-after-5-quick-taps")
        XCTAssertTrue(revealInUpNext(app, Self.queueCount - 1, maxSwipes: 12), "list still scrolls")
        tapHeader(app)
        waitCollapsed(app, at: collapsedTop, "still closes")
        attachScreenshot("06e-final")
    }

    /// Closing the Player while open and reopening it from the MiniPlayer starts at the peek.
    @MainActor
    func testReopenPlayerStartsCollapsed() {
        let app = launchPlayerWithLongQueue()
        let collapsedTop = settledFrame(peek(app)).minY
        app.buttons["player.playlist"].tap()
        waitExpanded(app, "playlist must open")
        closePlayer(app)
        attachScreenshot("07a-player-closed")
        app.buttons["miniplayer.open"].tap()
        XCTAssertTrue(element(app, "player.root").waitForExistence(timeout: 5), "MiniPlayer opens the Player")
        peek(app).waitToExist(timeout: 5)
        _ = settledFrame(peek(app))
        attachScreenshot("07b-player-reopened")
        waitCollapsed(app, at: collapsedTop, "reopened Player starts at the peek")
        // And it still opens.
        tapHeader(app)
        waitExpanded(app, "opens after reopening")
        attachScreenshot("07c-reopened-then-opened")
    }

    /// Best effort: a screenshot taken while the finger is still down, half way up.
    @MainActor
    func testMidDragScreenshot() {
        let app = launchPlayerWithLongQueue()
        let collapsedTop = settledFrame(peek(app)).minY
        let travel = collapsedTop - closeRowBottom(app)
        dismissBanners()
        let shots = MidGestureScreenshots(delays: [0.6, 1.4, 2.2])
        shots.start()
        // ~0.9 s of slow movement over 50 % of the travel, then a 2 s hold: the last two shots
        // are taken with the finger still, half way up, where the panel top must be.
        let start = peek(app).coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).withOffset(CGVector(dx: 0, dy: 20))
        start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: 0, dy: -travel * 0.5)), withVelocity: XCUIGestureVelocity(travel * 0.5 / 0.9), thenHoldForDuration: 2.0)
        for (index, data) in shots.collect().enumerated() {
            let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.png")
            attachment.name = "08-mid-drag-\(index)"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        attachScreenshot("08-after-release")
    }

    /// The open list scrolled away from its top: a pull down scrolls it and the sheet stays open.
    /// At the top: the same pull drags the whole sheet down with the finger (screenshots taken
    /// while the finger is down) and past the threshold it closes; a fling down closes too.
    @MainActor
    func testScrollPullDownHandsOffToSheet() {
        let app = launchPlayerWithLongQueue()
        let collapsedTop = settledFrame(peek(app)).minY
        let travel = collapsedTop - closeRowBottom(app)
        app.buttons["player.playlist"].tap()
        waitExpanded(app, "playlist must open")

        // Scrolled down: a slow 150 pt pull on the list scrolls it back up, the sheet stays open.
        XCTAssertTrue(revealInUpNext(app, 20, maxSwipes: 8), "row 20")
        let row = app.buttons["upNext.item.20"]
        let rowBefore = row.frame.minY
        dismissBanners()
        let middle = list(app).coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        middle.press(forDuration: 0.05, thenDragTo: middle.withOffset(CGVector(dx: 0, dy: 150)), withVelocity: XCUIGestureVelocity(600), thenHoldForDuration: 0.4)
        attachScreenshot("11a-pull-down-mid-list")
        waitExpanded(app, "a pull down on a list scrolled away from its top only scrolls")
        XCTAssertGreaterThan(row.frame.minY, rowBefore + 100, "the list scrolled with the pull: row 20 \(rowBefore) → \(row.frame.minY)")

        // At the top: the pull hands off to the sheet, which follows the finger and closes.
        XCTAssertTrue(revealInUpNext(app, 0, upwards: true, maxSwipes: 12), "row 0")
        _ = settledFrame(app.buttons["upNext.item.0"])
        waitExpanded(app, "still open at the top")
        dismissBanners()
        let shots = MidGestureScreenshots(delays: [0.7, 1.5, 2.3])
        shots.start()
        let start = list(app).coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3))
        start.press(forDuration: 0.05, thenDragTo: start.withOffset(CGVector(dx: 0, dy: travel * 0.5)), withVelocity: XCUIGestureVelocity(travel * 0.5 / 0.9), thenHoldForDuration: 2.0)
        for (index, data) in shots.collect().enumerated() {
            let attachment = XCTAttachment(data: data, uniformTypeIdentifier: "public.png")
            attachment.name = "11b-hand-off-mid-pull-\(index)"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        waitCollapsed(app, at: collapsedTop, "a 50 % pull down from the top of the list must close")
        attachScreenshot("11c-after-hand-off-pull")

        // A fast flick down from the top of the open list closes too.
        app.buttons["player.playlist"].tap()
        waitExpanded(app, "reopen")
        XCTAssertTrue(revealInUpNext(app, 0, upwards: true, maxSwipes: 12), "row 0 again")
        dismissBanners()
        let flick = list(app).coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3))
        flick.press(forDuration: 0.05, thenDragTo: flick.withOffset(CGVector(dx: 0, dy: 120)), withVelocity: Self.fling, thenHoldForDuration: 0)
        waitCollapsed(app, at: collapsedTop, "a fling down from the top of the list must close")
        attachScreenshot("11d-after-flick-from-top")
    }

    /// Landscape (compact height, where a sheet always fills the height): the peek is drawn in
    /// the Player under the controls, the close button stays tappable, a tap opens the queue as a
    /// full sheet and a pull down closes it back to the peek; portrait again shows the peek sheet.
    @MainActor
    func testLandscapeKeepsThePeek() {
        let app = launchPlayerWithLongQueue()
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        _ = poll(timeout: 3) { app.frame.width > app.frame.height }
        _ = settledFrame(peek(app))
        attachScreenshot("12a-landscape")
        XCTAssertTrue(peek(app).exists, "peek in landscape")
        XCTAssertFalse(list(app).exists, "collapsed in landscape")
        XCTAssertTrue(app.buttons["player.close"].isHittable, "close tappable in landscape: peek \(peek(app).frame)")
        XCTAssertGreaterThan(peek(app).frame.minY, app.frame.height * 0.4, "the peek stays low: \(peek(app).frame) in \(app.frame)")

        tapHeader(app)
        XCTAssertTrue(list(app).waitForExistence(timeout: 4), "a tap opens the queue in landscape")
        attachScreenshot("12b-landscape-open")
        let top = list(app).coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2))
        top.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 1)), withVelocity: Self.fling, thenHoldForDuration: 0)
        XCTAssertTrue(poll(timeout: 4) { !self.list(app).exists }, "a pull down closes the landscape sheet")
        attachScreenshot("12c-landscape-closed")
        XCTAssertTrue(peek(app).exists, "peek back in landscape")

        XCUIDevice.shared.orientation = .portrait
        _ = poll(timeout: 3) { app.frame.height > app.frame.width }
        _ = settledFrame(peek(app))
        attachScreenshot("12d-back-to-portrait")
        XCTAssertGreaterThanOrEqual(peek(app).frame.minY + 1, controlsBottom(app), "peek under the controls again: \(peek(app).frame)")
    }

    /// Speed and sleep timer sheets open from the Player while the Up next sheet is up (they are
    /// presented from the Up next sheet then) and close back to the peek.
    @MainActor
    func testSpeedAndSleepSheetsOpenOverThePeek() {
        let app = launchPlayerWithLongQueue()
        let collapsedTop = settledFrame(peek(app)).minY
        app.buttons["player.speed"].tap()
        let speed = element(app, "speed.slider")
        let speedOpened = speed.waitForExistence(timeout: 4)
        attachScreenshot("13a-speed-sheet")
        XCTAssertTrue(speedOpened, "the speed sheet opens over the peek")
        speed.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).withOffset(CGVector(dx: 0, dy: -60))
            .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 1)), withVelocity: Self.fling, thenHoldForDuration: 0)
        XCTAssertTrue(poll(timeout: 4) { !speed.exists }, "speed sheet closed")
        app.buttons["player.sleepTimer"].tap()
        let sleep = element(app, "sleepTimer.sheet")
        let sleepOpened = sleep.waitForExistence(timeout: 4)
        attachScreenshot("13b-sleep-sheet")
        XCTAssertTrue(sleepOpened, "the sleep timer sheet opens over the peek")
        sleep.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).withOffset(CGVector(dx: 0, dy: 20))
            .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 1)), withVelocity: Self.fling, thenHoldForDuration: 0)
        XCTAssertTrue(poll(timeout: 4) { !sleep.exists }, "sleep sheet closed")
        waitCollapsed(app, at: collapsedTop, "back to the peek")
        attachScreenshot("13c-back-to-peek")
    }

    // MARK: - Hitches

    /// Scroll hitches of the open list (fast flings down and up).
    @MainActor
    func testHitchesScrollingOpenList() {
        let app = launchPlayerWithLongQueue(probe: false)
        app.buttons["player.playlist"].tap()
        XCTAssertTrue(poll(timeout: 4) { self.list(app).exists })
        _ = settledFrame(peek(app))
        let root = list(app)
        let options = XCTMeasureOptions()
        options.iterationCount = 5
        dismissBanners()
        measure(metrics: [XCTOSSignpostMetric.scrollingAndDecelerationMetric], options: options) {
            root.swipeUp(velocity: .fast)
            root.swipeUp(velocity: .fast)
            root.swipeDown(velocity: .fast)
            root.swipeDown(velocity: .fast)
        }
        attachScreenshot("09-after-scroll-measure")
    }

    /// Hitches of the open/close animation (the `UpNextPanel` animation signpost), by tap.
    @MainActor
    func testHitchesPanelAnimation() {
        let app = launchPlayerWithLongQueue(probe: false)
        let collapsedTop = settledFrame(peek(app)).minY
        let metric = XCTOSSignpostMetric(subsystem: "tss.t.podcast", category: "UpNextPanel", name: "panel")
        let options = XCTMeasureOptions()
        options.iterationCount = 5
        measure(metrics: [metric], options: options) {
            tapHeader(app)
            RunLoop.current.run(until: Date().addingTimeInterval(0.8))
            tapHeader(app)
            RunLoop.current.run(until: Date().addingTimeInterval(0.8))
        }
        waitCollapsed(app, at: collapsedTop, "ends closed")
        attachScreenshot("10-after-animation-measure")
    }

    /// Hitches of the drag: open and close by dragging the header slowly (the settle animation
    /// after each release is the signposted interval).
    @MainActor
    func testHitchesDragOpenClose() {
        let app = launchPlayerWithLongQueue(probe: false)
        let collapsedTop = settledFrame(peek(app)).minY
        let travel = collapsedTop - closeRowBottom(app)
        let metric = XCTOSSignpostMetric(subsystem: "tss.t.podcast", category: "UpNextPanel", name: "panel")
        let options = XCTMeasureOptions()
        options.iterationCount = 5
        measure(metrics: [metric], options: options) {
            dragHeader(app, by: -travel * 0.7, velocity: XCUIGestureVelocity(800), hold: 0)
            RunLoop.current.run(until: Date().addingTimeInterval(0.8))
            dragHeader(app, by: travel * 0.7, velocity: XCUIGestureVelocity(800), hold: 0)
            RunLoop.current.run(until: Date().addingTimeInterval(0.8))
        }
        attachScreenshot("11-after-drag-measure")
    }
}

/// Screenshots taken on a background thread while the main thread performs a gesture
/// (XCUICoordinate gestures block the main thread until the finger lifts).
final class MidGestureScreenshots: @unchecked Sendable {
    private let delays: [TimeInterval]
    private let lock = NSLock()
    private var shots: [Data] = []
    private let done = DispatchSemaphore(value: 0)

    init(delays: [TimeInterval]) {
        self.delays = delays
    }

    func start() {
        let delays = delays
        Thread.detachNewThread { [self] in
            let begin = Date()
            for delay in delays {
                let wait = delay - Date().timeIntervalSince(begin)
                if wait > 0 { Thread.sleep(forTimeInterval: wait) }
                if let data = Self.screenshotOffMain() {
                    lock.lock()
                    shots.append(data)
                    lock.unlock()
                }
            }
            done.signal()
        }
    }

    func collect(timeout: TimeInterval = 10) -> [Data] {
        _ = done.wait(timeout: .now() + timeout)
        lock.lock()
        defer { lock.unlock() }
        return shots
    }

    /// `XCUIScreen` is main-actor isolated in the SDK, but the screenshot is an XPC request to
    /// testmanagerd; calling it here is the only way to see the screen during a drag.
    private static func screenshotOffMain() -> Data? {
        let body: @MainActor () -> Data = { XCUIScreen.main.screenshot().pngRepresentation }
        let unchecked = unsafeBitCast(body, to: (@Sendable () -> Data).self)
        return unchecked()
    }
}
