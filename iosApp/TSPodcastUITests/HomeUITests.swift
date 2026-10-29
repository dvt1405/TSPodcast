import XCTest

/// M1-AC16, M1-AC18: Home/Discover against the real PodcastIndex API (Android
/// `DiscoverPodcastsScreen`): section order, content, attribution, detail navigation and
/// pull-to-refresh.
final class HomeUITests: TSPodcastUITestCase {
    @MainActor
    func testSectionsRenderInAndroidOrder() {
        let app = launchMain()
        var appearance: [String] = []

        for (id, title) in Self.homeSections {
            let header = app.staticTexts["home.section.\(id)"]
            XCTAssertTrue(scrollHome(app, to: header), "header \(id) not reached")
            XCTAssertEqual(header.label, title)
            if id == "music" {
                XCTAssertEqual(app.staticTexts["home.section.music.subtitle"].label, "Feed nhạc theo chuẩn podcast:medium")
            }
            // A header shown together with the previous one must sit below it.
            if let previous = appearance.last {
                let previousHeader = app.staticTexts["home.section.\(previous)"]
                if previousHeader.exists, !previousHeader.frame.isEmpty {
                    XCTAssertGreaterThan(header.frame.minY, previousHeader.frame.maxY, "\(id) below \(previous)")
                }
            }
            appearance.append(id)

            let outcome = waitForSectionContent(app, id)
            XCTAssertNotNil(outcome, "\(id) still loading after \(Int(Self.networkTimeout)) s")
            XCTAssertNotEqual(outcome, "error", "\(id) failed to load")
            if ["trending", "music", "forYou"].contains(id) {
                XCTAssertEqual(outcome, "item", "\(id) must have items")
            }
            XCTAssertFalse(element(app, "home.section.\(id).loading").exists, "\(id) still shimmers")
            attachScreenshot("home-section-\(id)")
        }
        XCTAssertEqual(appearance, Self.homeSections.map(\.id))
    }

    @MainActor
    func testAttributionFooterIsLast() {
        let app = launchMain()
        XCTAssertEqual(waitForSectionContent(app, "trending"), "item")

        let footer = scrollHomeToBottom(app)
        attachScreenshot("home-footer")

        XCTAssertTrue(isVisibleInHome(app, footer), "footer not reached: \(footer.exists) \(footer.frame) tab \(app.tabBars.firstMatch.frame) nav \(app.navigationBars.firstMatch.frame)")
        XCTAssertEqual(footer.label, "Dữ liệu podcast cung cấp bởi Podcast Index")
        let forYouItems = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'home.section.forYou.item.'"))
        let lastItemBottom = forYouItems.allElementsBoundByIndex.map(\.frame.maxY).max()
        XCTAssertNotNil(lastItemBottom, "the last forYou items must be on screen above the footer")
        if let lastItemBottom {
            XCTAssertGreaterThanOrEqual(footer.frame.minY, lastItemBottom, "footer below the last forYou item")
        }
    }

    @MainActor
    func testTapTrendingOpensDetailStub() {
        let app = launchMain()
        let item = app.buttons["home.section.trending.item.0"]
        item.waitToExist(timeout: Self.networkTimeout, "trending needs an item")
        let title = item.label
        XCTAssertFalse(title.isEmpty)

        item.tap()

        let detail = element(app, "detail.root")
        detail.waitToExist(timeout: 5, "tap must push the detail stub")
        XCTAssertEqual(detail.staticTexts["detail.title"].label, title)
        attachScreenshot("detail-stub")

        app.navigationBars.buttons.element(boundBy: 0).tap()
        element(app, "home.root").waitToExist(timeout: 5)
        XCTAssertTrue(poll(timeout: 5) { !detail.exists }, "Back returns to Home")
    }

    @MainActor
    func testPullToRefreshReloads() {
        let app = launchMain()
        let list = element(app, "home.list")
        XCTAssertTrue(waitForValue(list, "generation=1", timeout: Self.networkTimeout), "first load: \(String(describing: list.value))")

        let start = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.15))
        let end = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
        start.press(forDuration: 0.1, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 0.3)

        XCTAssertTrue(waitForValue(list, "generation=2", timeout: Self.networkTimeout), "after pull: \(String(describing: list.value))")
        XCTAssertEqual(waitForSectionContent(app, "trending"), "item")
        XCTAssertEqual(waitForSectionContent(app, "music"), "item")
        let forYouHeader = app.staticTexts["home.section.forYou"]
        XCTAssertTrue(scrollHome(app, to: forYouHeader))
        XCTAssertEqual(waitForSectionContent(app, "forYou"), "item")
        attachScreenshot("home-after-refresh")
    }
}
