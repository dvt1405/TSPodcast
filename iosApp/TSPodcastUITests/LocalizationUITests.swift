import XCTest

/// M1-AC8: the English localization of the shell and Home (vi is checked by the other suites).
final class LocalizationUITests: TSPodcastUITestCase {
    @MainActor
    func testEnglishLocale() {
        let app = launchMain(language: "en", locale: "en_US")

        let tabBar = app.tabBars.firstMatch
        let labels = tabBar.buttons.allElementsBoundByIndex.sorted { $0.frame.minX < $1.frame.minX }.map(\.label)
        XCTAssertEqual(labels, ["Favorites", "Discover", "Search"])
        XCTAssertTrue(app.navigationBars["Discover"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["home.section.trending"].label, "Trending podcasts")
        attachScreenshot("home-en")

        let footer = scrollHomeToBottom(app)
        XCTAssertTrue(isVisibleInHome(app, footer), "footer not reached: \(footer.exists) \(footer.frame) tab \(app.tabBars.firstMatch.frame) nav \(app.navigationBars.firstMatch.frame)")
        XCTAssertEqual(footer.label, "Podcast data provided by Podcast Index")
    }
}
