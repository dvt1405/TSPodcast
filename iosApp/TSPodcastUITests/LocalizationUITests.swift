import XCTest

/// M1-AC8, M2-AC16: the English localization of the shell, Home, Search and Detail (vi is
/// checked by the other suites).
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

    /// M2-AC16: the English strings of Search and Detail (M2.md §4.8). One launch with
    /// `-uitest-fail-detail-once`: Search does not use the Detail adapter, so the first Detail
    /// load opened from "Discover" is the one that fails.
    @MainActor
    func testEnglishSearchAndDetail() {
        let app = launchMain([.failDetailOnce], language: "en", locale: "en_US")

        openSearchTab(app, label: "Search")
        XCTAssertTrue(app.navigationBars["Search"].waitForExistence(timeout: 5), "title 'Search'")
        let field = searchField(app)
        field.waitToExist(timeout: 5, "search field")
        XCTAssertEqual(field.placeholderValue, "Search podcasts, artists")
        XCTAssertEqual(app.buttons["search.mode.all"].label, "All")
        XCTAssertEqual(app.buttons["search.mode.music"].label, "Music")
        let suggestion = element(app, "search.suggestion")
        suggestion.waitToExist(timeout: 5)
        XCTAssertEqual(suggestion.label, "Try searching: great podcasts")
        XCTAssertTrue(app.staticTexts["Categories"].waitForExistence(timeout: 5), "'Categories' header")
        attachScreenshot("M2-search-idle-en")

        typeSearch(app, "zzqxjvkwpt")
        XCTAssertEqual(waitForSearchOutcome(app), "empty")
        XCTAssertEqual(element(app, "search.empty").label, "No results for: zzqxjvkwpt")
        app.buttons["search.mode.music"].tap()
        let hint = element(app, "search.empty.musicHint")
        hint.waitToExist(timeout: Self.networkTimeout, "music hint")
        XCTAssertEqual(hint.label, "The podcast:medium music catalogue is small, try English keywords")
        // Return hides the keyboard, which covers the tab bar (the same pair is not resent).
        searchField(app).typeText("\n")
        attachScreenshot("M2-search-empty-en")

        tabButton(app, "Discover").tap()
        element(app, "home.root").waitToExist(timeout: 5, "'Discover' tab")
        XCTAssertNotNil(openTrendingDetail(app, 0), "trending item 0")

        let alert = app.alerts["Oops!"]
        alert.waitToExist(timeout: 10, "error alert 'Oops!'")
        XCTAssertTrue(alert.staticTexts["Couldn't load the episodes. Please try again."].exists, "alert message")
        XCTAssertTrue(alert.buttons["Retry"].exists)
        XCTAssertTrue(alert.buttons["Cancel"].exists)
        attachScreenshot("M2-detail-error-alert-en")
        alert.buttons["Cancel"].tap()

        XCTAssertTrue(poll(timeout: 5) { !alert.exists }, "alert closes")
        let message = element(app, "detail.episodes.error")
        message.waitToExist(timeout: 5, "inline error")
        XCTAssertEqual(message.label, "Couldn't load episodes")
        XCTAssertEqual(app.buttons["detail.episodes.retry"].label, "Retry")
    }
}
