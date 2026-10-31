import XCTest

/// M2-AC11, AC12, AC13 (loading), AC14, AC15: the Search tab against the real PodcastIndex API
/// (features-spec §3.9; Android `SearchScreen`, `SearchModeFilter`, `SearchPodcastItem`,
/// `EmptySearchWidget`, the Search route of `HomeNavigationScreen`). Queries measured by the PO
/// on 30/10/2026: "vietnam" (E7: 51), "piano" (E8: 41), "casino" (E7: 28, 20 blacklisted by
/// title), "zzqxjvkwpt" (0 in both modes).
final class SearchUITests: TSPodcastUITestCase {
    // MARK: - AC11 idle

    @MainActor
    func testIdleShowsSuggestionModesAndCategories() {
        let app = launchMain()
        openSearchTab(app)

        XCTAssertTrue(app.navigationBars["Tìm kiếm"].waitForExistence(timeout: 5), "title 'Tìm kiếm'")
        let field = searchField(app)
        field.waitToExist(timeout: 5, "search field")
        XCTAssertEqual(field.placeholderValue, "Tìm podcast, nghệ sĩ")

        let suggestion = element(app, "search.suggestion")
        suggestion.waitToExist(timeout: 5)
        XCTAssertEqual(suggestion.label, "Thử tìm kiếm: Podcast hay")

        let all = app.buttons["search.mode.all"]
        let music = app.buttons["search.mode.music"]
        XCTAssertEqual(all.label, "Tất cả")
        XCTAssertEqual(music.label, "Nhạc")
        XCTAssertTrue(all.isSelected, "'Tất cả' is the default mode")
        XCTAssertFalse(music.isSelected)

        let arts = app.buttons["search.category.1"]
        arts.waitToExist(timeout: Self.networkTimeout, "category chip 1")
        XCTAssertEqual(arts.label, "Arts")
        let news = app.buttons["search.category.55"]
        news.waitToExist(timeout: 5, "category chip 55")
        XCTAssertEqual(news.label, "News")
        XCTAssertFalse(element(app, "search.results.header").exists)
        attachScreenshot("M2-search-idle")
    }

    @MainActor
    func testClearingQueryReturnsToIdle() {
        let app = launchMain()
        openSearchTab(app)
        typeSearch(app, "vietnam")
        XCTAssertEqual(waitForSearchOutcome(app), "results")

        let clear = searchField(app).buttons.firstMatch
        clear.waitToExist(timeout: 5, "clear button of the search field")
        clear.tap()

        element(app, "search.suggestion").waitToExist(timeout: 5, "suggestion is back")
        app.buttons["search.category.1"].waitToExist(timeout: 5, "categories are back")
        XCTAssertFalse(element(app, "search.results.header").exists)
        XCTAssertFalse(element(app, "search.result.0").exists)
    }

    // MARK: - AC12 results, music, empty, blacklist

    @MainActor
    func testTypingShowsTopResults() {
        let app = launchMain()
        openSearchTab(app)
        typeSearch(app, "vietnam")

        XCTAssertTrue(waitForResultsHeader(app, "Kết quả hàng đầu cho: vietnam"), "header of mode All")
        let firstTitle = app.staticTexts["search.result.0.title"]
        firstTitle.waitToExist(timeout: 5, "first result")
        XCTAssertFalse(firstTitle.label.isEmpty)
        XCTAssertTrue(element(app, "search.result.0").exists)
        // Return: the same pair is not sent again, the keyboard goes away for the screenshot.
        searchField(app).typeText("\n")
        attachScreenshot("M2-search-results")
    }

    @MainActor
    func testMusicModeResearchesImmediately() {
        let app = launchMain()
        openSearchTab(app)
        typeSearch(app, "piano")
        XCTAssertTrue(waitForResultsHeader(app, "Kết quả hàng đầu cho: piano"), "results of mode All first")

        let music = app.buttons["search.mode.music"]
        music.tap()

        XCTAssertTrue(waitForResultsHeader(app, "Nhạc cho: piano"), "switching to Nhạc searches again")
        element(app, "search.result.0").waitToExist(timeout: 5, "music results")
        XCTAssertTrue(music.isSelected)
        XCTAssertFalse(app.buttons["search.mode.all"].isSelected)
        searchField(app).typeText("\n")
        attachScreenshot("M2-search-music")
    }

    @MainActor
    func testEmptyResultsAndMusicHint() {
        let app = launchMain()
        openSearchTab(app)
        typeSearch(app, "zzqxjvkwpt")

        XCTAssertEqual(waitForSearchOutcome(app), "empty")
        let empty = element(app, "search.empty")
        XCTAssertEqual(empty.label, "Không có kết quả cho: zzqxjvkwpt")
        XCTAssertFalse(element(app, "search.empty.musicHint").exists, "no hint in mode All")

        app.buttons["search.mode.music"].tap()

        let hint = element(app, "search.empty.musicHint")
        hint.waitToExist(timeout: Self.networkTimeout, "music hint")
        XCTAssertEqual(hint.label, "Kho nhạc theo chuẩn podcast:medium còn ít, thử từ khoá tiếng Anh")
        XCTAssertTrue(element(app, "search.empty").exists)
        XCTAssertEqual(element(app, "search.empty").label, "Không có kết quả cho: zzqxjvkwpt")
        searchField(app).typeText("\n")
        attachScreenshot("M2-search-empty")
    }

    @MainActor
    func testBlacklistedResultsHidden() {
        let app = launchMain()
        openSearchTab(app)
        typeSearch(app, "casino")
        let outcome = waitForSearchOutcome(app)
        XCTAssertTrue(outcome == "results" || outcome == "empty", "got \(String(describing: outcome))")
        searchField(app).typeText("\n")

        var titles: [String] = []
        if outcome == "results" {
            var index = 0
            var swipesWithoutProgress = 0
            while swipesWithoutProgress < 4 {
                let title = app.staticTexts["search.result.\(index).title"]
                if title.exists {
                    titles.append(title.label)
                    index += 1
                    swipesWithoutProgress = 0
                } else {
                    element(app, "search.root").swipeUp(velocity: .slow)
                    swipesWithoutProgress += 1
                }
            }
            XCTAssertFalse(titles.isEmpty)
        }
        for title in titles {
            let lowered = title.lowercased()
            for word in Self.blacklistWords {
                XCTAssertFalse(lowered.contains(word), "'\(title)' contains blacklisted '\(word)'")
            }
        }
        let attachment = XCTAttachment(string: titles.joined(separator: "\n"))
        attachment.name = "M2-search-casino-titles"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    // MARK: - AC13 loading

    @MainActor
    func testLoadingIndicatorShown() {
        let app = launchMain([.slowNetwork])
        openSearchTab(app)
        typeSearch(app, "vietnam")

        XCTAssertTrue(element(app, "search.loading").waitForExistence(timeout: 3), "spinner while the request runs")
        attachScreenshot("M2-search-loading")
        let first = element(app, "search.result.0")
        XCTAssertTrue(poll(timeout: Self.networkTimeout) { first.exists }, "results after the slow request")
        XCTAssertFalse(element(app, "search.loading").exists)
    }

    // MARK: - AC14 category

    @MainActor
    func testCategoryOpensTrendingByCategory() {
        let app = launchMain()
        openSearchTab(app)
        let arts = app.buttons["search.category.1"]
        arts.waitToExist(timeout: Self.networkTimeout, "category chip 1")
        arts.tap()

        element(app, "categoryPodcasts.root").waitToExist(timeout: 10, "category list")
        XCTAssertTrue(app.navigationBars["Arts"].waitForExistence(timeout: 5), "titled with the category")
        let item = app.buttons["categoryPodcasts.item.0"]
        item.waitToExist(timeout: Self.networkTimeout, "first podcast of Arts")
        XCTAssertFalse(element(app, "categoryPodcasts.loading").exists)
        attachScreenshot("M2-category-arts")
        let title = item.label
        XCTAssertFalse(title.isEmpty)

        item.tap()
        element(app, "detail.root").waitToExist(timeout: 10, "tap opens Detail")
        XCTAssertEqual(app.staticTexts["detail.title"].label, title)

        tapBack(app)
        element(app, "categoryPodcasts.root").waitToExist(timeout: 5, "Back returns to the category list")
        XCTAssertTrue(poll(timeout: 5) { !self.element(app, "detail.root").exists })

        tapBack(app)
        XCTAssertTrue(poll(timeout: 5) { !self.element(app, "categoryPodcasts.root").exists }, "Back returns to Search")
        element(app, "search.root").waitToExist(timeout: 5)
        XCTAssertTrue(app.buttons["search.category.1"].exists)
    }

    // MARK: - AC15 detail and state

    @MainActor
    func testResultOpensDetailAndStatePersists() {
        let app = launchMain()
        openSearchTab(app)
        typeSearch(app, "vietnam")
        XCTAssertTrue(waitForResultsHeader(app, "Kết quả hàng đầu cho: vietnam"))
        let firstTitle = app.staticTexts["search.result.0.title"]
        firstTitle.waitToExist(timeout: 5)
        let title = firstTitle.label

        element(app, "search.result.0").tap()

        element(app, "detail.root").waitToExist(timeout: 10, "a result opens Detail")
        XCTAssertEqual(app.staticTexts["detail.title"].label, title)
        let content = waitForDetailContent(app)
        XCTAssertTrue(content == "episode" || content == "empty", "Detail loaded: \(String(describing: content))")
        attachScreenshot("M2-search-detail")

        tabButton(app, "Khám phá").tap()
        element(app, "home.root").waitToExist(timeout: 5)
        XCTAssertFalse(element(app, "detail.root").exists, "Home has its own stack")
        tabButton(app, "Tìm kiếm").tap()
        element(app, "detail.root").waitToExist(timeout: 5, "Search still shows the pushed Detail")

        tapBack(app)
        XCTAssertTrue(poll(timeout: 5) { !self.element(app, "detail.root").exists })
        XCTAssertTrue(waitForResultsHeader(app, "Kết quả hàng đầu cho: vietnam", timeout: 5), "results kept")
        XCTAssertEqual(searchField(app).value as? String, "vietnam", "query kept")
        XCTAssertEqual(app.staticTexts["search.result.0.title"].label, title)
    }

    @MainActor
    func testSearchStatePersistsAcrossTabs() {
        let app = launchMain()
        openSearchTab(app)
        app.buttons["search.mode.music"].tap()
        typeSearch(app, "piano")
        XCTAssertTrue(waitForResultsHeader(app, "Nhạc cho: piano"))
        // Return hides the keyboard, which covers the tab bar (the same pair is not resent).
        searchField(app).typeText("\n")

        tabButton(app, "Yêu thích").tap()
        element(app, "favourite.root").waitToExist(timeout: 5)
        tabButton(app, "Tìm kiếm").tap()
        element(app, "search.root").waitToExist(timeout: 5)

        XCTAssertTrue(app.buttons["search.mode.music"].isSelected, "mode kept")
        XCTAssertFalse(app.buttons["search.mode.all"].isSelected)
        XCTAssertTrue(waitForResultsHeader(app, "Nhạc cho: piano", timeout: 5), "results kept")
        XCTAssertEqual(searchField(app).value as? String, "piano", "query kept")
    }
}
