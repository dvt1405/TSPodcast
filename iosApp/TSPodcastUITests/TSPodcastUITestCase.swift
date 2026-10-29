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
