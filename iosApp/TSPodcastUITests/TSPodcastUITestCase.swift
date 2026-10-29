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
