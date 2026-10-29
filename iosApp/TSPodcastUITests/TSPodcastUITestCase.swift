import XCTest

/// Base class of the XCUITests: launches the app with the hooks of M1 §4.7 in Vietnamese
/// (or another language) and offers small waiting helpers.
class TSPodcastUITestCase: XCTestCase {
    /// Tests hit the real PodcastIndex API: data may take a while.
    static let networkTimeout: TimeInterval = 30

    enum Hook: String {
        case reset = "-uitest-reset"
        case skipOnboarding = "-uitest-skip-onboarding"
        case miniPlayerPlaceholder = "-uitest-miniplayer-placeholder"
        case slowNetwork = "-uitest-slow-network"
        case failDetailOnce = "-uitest-fail-detail-once"
        case detailEmpty = "-uitest-detail-empty"
        case detailExplicit = "-uitest-detail-explicit"
        case playbackProbe = "-uitest-playback-probe"
    }

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    /// Launches a fresh app. Language arguments come first and the hooks last, so the
    /// `-uitest-*` flags are never read as the value of `-AppleLanguages`.
    @MainActor
    @discardableResult
    func launch(
        _ hooks: [Hook] = [],
        language: String = "vi",
        locale: String = "vi_VN",
        environment: [String: String] = [:]
    ) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(\(language))", "-AppleLocale", locale] + hooks.map(\.rawValue)
        app.launchEnvironment = environment
        app.launch()
        return app
    }

    /// Attaches a screenshot of the whole screen, kept in the result bundle.
    @MainActor
    func attachScreenshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

extension XCUIElement {
    /// Waits until the element exists, failing the test with `message` otherwise.
    @MainActor
    func waitToExist(timeout: TimeInterval = 10, _ message: String = "", file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(waitForExistence(timeout: timeout), message.isEmpty ? "\(self) not found" : message, file: file, line: line)
    }
}

// MARK: - Onboarding and category selection (M1 T3)

extension TSPodcastUITestCase {
    /// Any element with `identifier`, whatever its type (the roots change type as screens grow).
    @MainActor
    func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    /// Whether onboarding page `index` fills the screen (the paged `TabView` keeps the
    /// neighbouring pages in the hierarchy, so existence alone does not say which page shows).
    @MainActor
    func isOnboardingPageOnScreen(_ app: XCUIApplication, _ index: Int) -> Bool {
        let page = app.otherElements["onboarding.page.\(index)"]
        return page.exists && abs(page.frame.minX - app.frame.minX) < 2
    }

    /// Waits until onboarding page `index` fills the screen.
    @MainActor
    func waitForOnboardingPage(_ app: XCUIApplication, _ index: Int, timeout: TimeInterval = 10, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(
            poll(timeout: timeout) { self.isOnboardingPageOnScreen(app, index) },
            "onboarding page \(index) is not on screen",
            file: file,
            line: line
        )
    }

    /// Re-evaluates `condition` every 0.2 s until it holds or `timeout` passes.
    @MainActor
    func poll(timeout: TimeInterval, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() {
            if Date() >= deadline { return false }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        return true
    }

    /// Swipes from page `index - 1` to page `index`. A swipe sent while the first screen is still
    /// fading in can be dropped, so it is sent again (at most 3 times) while the previous page
    /// still shows; it never swipes past `index`.
    @MainActor
    func swipeToOnboardingPage(_ app: XCUIApplication, _ index: Int, file: StaticString = #filePath, line: UInt = #line) {
        for _ in 0..<3 {
            if isOnboardingPageOnScreen(app, index) { break }
            if isOnboardingPageOnScreen(app, index - 1) {
                app.swipeLeft()
            }
            _ = poll(timeout: 3) { self.isOnboardingPageOnScreen(app, index) }
        }
        waitForOnboardingPage(app, index, timeout: 2, file: file, line: line)
    }

    /// Swipes through the 4 onboarding pages and taps "Tiếp tục"; ends on the category grid.
    @MainActor
    func finishOnboarding(_ app: XCUIApplication) {
        waitForOnboardingPage(app, 0)
        for page in 1...3 {
            swipeToOnboardingPage(app, page)
        }
        let continueButton = app.buttons["onboarding.continue"]
        continueButton.waitToExist(timeout: 5, "'Tiếp tục' must show on the last page")
        continueButton.tap()
        app.scrollViews["category.grid"].waitToExist(timeout: 10, "'Tiếp tục' must open the category grid")
    }

    /// Grid cell of category `id`, waiting for E9 (real network).
    @MainActor
    func categoryCell(_ app: XCUIApplication, _ id: Int) -> XCUIElement {
        let cell = app.buttons["category.cell.\(id)"]
        cell.waitToExist(timeout: Self.networkTimeout, "category \(id) not in the grid")
        return cell
    }
}

// MARK: - Main shell and Home (M1 T4)

extension TSPodcastUITestCase {
    /// The 5 Home sections in Android's order, with their vi headers.
    static let homeSections: [(id: String, title: String)] = [
        ("trending", "Podcasts thịnh hành"),
        ("music", "Kho nhạc"),
        ("live", "Live Podcasts"),
        ("recent", "Podcasts xu hướng gần đây"),
        ("forYou", "Có thể bạn yêu thích"),
    ]

    /// Launches on Main (`-uitest-skip-onboarding` + `hooks`) and waits for Home.
    @MainActor
    @discardableResult
    func launchMain(
        _ hooks: [Hook] = [],
        language: String = "vi",
        locale: String = "vi_VN",
        environment: [String: String] = [:]
    ) -> XCUIApplication {
        let app = launch([.skipOnboarding] + hooks, language: language, locale: locale, environment: environment)
        element(app, "home.root").waitToExist(timeout: 10, "skip-onboarding must open Home")
        return app
    }

    /// Tab bar button, by its localized label.
    @MainActor
    func tabButton(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.tabBars.firstMatch.buttons[label]
    }

    /// Drags the Home list up by about `fraction` of its height, without a fling, so lazily
    /// created rows are not skipped.
    @MainActor
    func dragHomeUp(_ app: XCUIApplication, fraction: CGFloat = 0.45) {
        let list = element(app, "home.list")
        let start = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75))
        let end = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75 - fraction))
        start.press(forDuration: 0.05, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 0.1)
    }

    /// Whether `element` is laid out inside the visible part of the Home list.
    @MainActor
    func isVisibleInHome(_ app: XCUIApplication, _ element: XCUIElement) -> Bool {
        guard element.exists else { return false }
        // The list runs under the navigation bar and the floating tab bar: only the band
        // between them counts, so a visible element can also be tapped.
        let list = self.element(app, "home.list").frame
        let top = max(list.minY, app.navigationBars.firstMatch.frame.maxY)
        let tabBar = app.tabBars.firstMatch
        let bottom = min(list.maxY, tabBar.exists ? tabBar.frame.minY : list.maxY)
        let frame = element.frame
        return !frame.isEmpty && frame.minY >= top && frame.maxY <= bottom
    }

    /// Drags Home up until `element` is fully visible (at most `maxDrags` drags).
    @MainActor
    @discardableResult
    func scrollHome(_ app: XCUIApplication, to element: XCUIElement, maxDrags: Int = 12) -> Bool {
        for _ in 0..<maxDrags {
            if isVisibleInHome(app, element) { return true }
            dragHomeUp(app)
        }
        return isVisibleInHome(app, element)
    }

    /// Flings Home to its end (the attribution footer).
    @MainActor
    func scrollHomeToBottom(_ app: XCUIApplication, maxSwipes: Int = 40) -> XCUIElement {
        let footer = element(app, "home.attribution")
        for _ in 0..<maxSwipes {
            // The lazy stack re-measures rows as they appear, which can push the footer down
            // again: it counts as reached only when it is still visible after a short settle.
            if isVisibleInHome(app, footer) {
                RunLoop.current.run(until: Date().addingTimeInterval(0.5))
                if isVisibleInHome(app, footer) { break }
            }
            element(app, "home.list").swipeUp(velocity: .fast)
        }
        return footer
    }

    /// Waits until section `id` has `.item.0` or `.empty` and no `.loading`; returns which one.
    @MainActor
    func waitForSectionContent(_ app: XCUIApplication, _ id: String, timeout: TimeInterval = networkTimeout) -> String? {
        let prefix = "home.section.\(id)"
        var outcome: String?
        _ = poll(timeout: timeout) {
            guard !self.element(app, "\(prefix).loading").exists else { return false }
            if self.element(app, "\(prefix).item.0").exists { outcome = "item"; return true }
            if self.element(app, "\(prefix).empty").exists { outcome = "empty"; return true }
            if self.element(app, "\(prefix).error").exists { outcome = "error"; return true }
            return false
        }
        return outcome
    }

    /// Waits until `element.value` equals `value`.
    @MainActor
    func waitForValue(_ element: XCUIElement, _ value: String, timeout: TimeInterval) -> Bool {
        poll(timeout: timeout) { element.exists && (element.value as? String) == value }
    }
}

// MARK: - Podcast detail (M2 T2)

extension TSPodcastUITestCase {
    /// Waits for Home item `identifier` (a button labelled with the podcast title), taps it and
    /// waits for `detail.root`. Returns the title.
    @MainActor
    @discardableResult
    func openDetail(_ app: XCUIApplication, from identifier: String = "home.section.trending.item.0", file: StaticString = #filePath, line: UInt = #line) -> String {
        let item = app.buttons[identifier]
        XCTAssertTrue(item.waitForExistence(timeout: Self.networkTimeout), "\(identifier) not found", file: file, line: line)
        let title = item.label
        XCTAssertFalse(title.isEmpty, "\(identifier) has no title", file: file, line: line)
        item.tap()
        XCTAssertTrue(element(app, "detail.root").waitForExistence(timeout: 10), "tap must push Detail", file: file, line: line)
        return title
    }

    /// Waits until Detail leaves the shimmer and returns what it shows: "episode" (row 0 is in
    /// the hierarchy), "empty", "explicit" or "error". Drags the page up when the list loaded
    /// below a long description (rows are lazy).
    @MainActor
    func waitForDetailContent(_ app: XCUIApplication, timeout: TimeInterval = networkTimeout) -> String? {
        var outcome: String?
        var drags = 0
        _ = poll(timeout: timeout) {
            if self.element(app, "detail.episodes.loading").exists { return false }
            if self.element(app, "detail.episode.0").exists { outcome = "episode"; return true }
            if self.element(app, "detail.episodes.empty").exists { outcome = "empty"; return true }
            if self.element(app, "detail.explicitHidden").exists { outcome = "explicit"; return true }
            if self.element(app, "detail.episodes.error").exists { outcome = "error"; return true }
            if app.alerts.firstMatch.exists { outcome = "error"; return true }
            if drags < 8, self.element(app, "detail.root").exists {
                drags += 1
                self.dragDetailUp(app)
            }
            return false
        }
        return outcome
    }

    /// Drags the Detail page up by about `fraction` of its height, slowly.
    @MainActor
    func dragDetailUp(_ app: XCUIApplication, fraction: CGFloat = 0.4) {
        let root = element(app, "detail.root")
        let start = root.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75))
        let end = root.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.75 - fraction))
        start.press(forDuration: 0.05, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 0.1)
    }

    /// Drags Detail up until `element` exists and can be tapped above the tab bar.
    @MainActor
    @discardableResult
    func revealInDetail(_ app: XCUIApplication, _ element: XCUIElement, maxDrags: Int = 10) -> Bool {
        func isReachable() -> Bool {
            guard element.exists, element.isHittable else { return false }
            let tabBar = app.tabBars.firstMatch
            let bottom = tabBar.exists ? tabBar.frame.minY : app.frame.maxY
            return element.frame.maxY <= bottom && element.frame.minY >= app.navigationBars.firstMatch.frame.maxY
        }
        for _ in 0..<maxDrags {
            if isReachable() { return true }
            dragDetailUp(app, fraction: 0.3)
        }
        return isReachable()
    }

    /// Taps trending item `index`, swiping the carousel when the card is off screen, and waits
    /// for Detail. Returns the title, or nil when the item does not exist.
    @MainActor
    func openTrendingDetail(_ app: XCUIApplication, _ index: Int) -> String? {
        let item = app.buttons["home.section.trending.item.\(index)"]
        guard item.waitForExistence(timeout: Self.networkTimeout) else { return nil }
        for _ in 0..<index {
            if item.frame.minX < app.frame.maxX - 80 { break }
            app.buttons["home.section.trending.item.\(max(0, index - 1))"].swipeLeft(velocity: .slow)
        }
        let frame = item.frame
        let visibleMinX = max(frame.minX, app.frame.minX)
        let visibleMaxX = min(frame.maxX, app.frame.maxX)
        guard visibleMaxX - visibleMinX > 40 else { return nil }
        let title = item.label
        item.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: (visibleMinX + visibleMaxX) / 2 - frame.minX, dy: frame.width * 0.4))
            .tap()
        guard element(app, "detail.root").waitForExistence(timeout: 10) else { return nil }
        return title
    }

    /// Back from Detail to Home.
    @MainActor
    func backToHome(_ app: XCUIApplication) {
        app.navigationBars.buttons.element(boundBy: 0).tap()
        let detail = element(app, "detail.root")
        XCTAssertTrue(poll(timeout: 5) { !detail.exists }, "Back returns to Home")
    }

    /// Opens the first trending item, from `start`, whose Detail lists episodes. Trending items
    /// can be explicit feeds that D6 hides on Detail (30/10/2026: item 0, feed 4201720), so
    /// tests that need episodes cannot rely on item 0. Returns the index and title.
    @MainActor
    func openDetailWithEpisodes(_ app: XCUIApplication, start: Int = 0, maxItems: Int = 6) -> (index: Int, title: String)? {
        for index in start..<(start + maxItems) {
            guard let title = openTrendingDetail(app, index) else { return nil }
            if waitForDetailContent(app) == "episode" { return (index, title) }
            backToHome(app)
        }
        return nil
    }

    /// N of the `count=N` value of `detail.episodes` (Debug), or nil.
    @MainActor
    func detailEpisodeCount(_ app: XCUIApplication) -> Int? {
        let list = element(app, "detail.episodes")
        guard list.waitForExistence(timeout: 5), let value = list.value as? String, value.hasPrefix("count=") else {
            return nil
        }
        return Int(value.dropFirst("count=".count))
    }
}
