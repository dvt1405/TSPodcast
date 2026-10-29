import XCTest

/// M1-AC14, M1-AC15: the main shell (Android `HomeNavigationScreen`/`BottomAppBarBlur`, without
/// Radio), a navigation stack per tab and the MiniPlayer slot above the tab bar.
final class MainShellUITests: TSPodcastUITestCase {
    private let tabLabels = ["Yêu thích", "Khám phá", "Tìm kiếm"]

    @MainActor
    func testThreeTabsInOrderHomeSelected() {
        let app = launchMain()
        let tabBar = app.tabBars.firstMatch

        let buttons = tabBar.buttons.allElementsBoundByIndex.sorted { $0.frame.minX < $1.frame.minX }
        XCTAssertEqual(buttons.map(\.label), tabLabels, "exactly 3 tabs, left to right")
        XCTAssertTrue(tabButton(app, "Khám phá").isSelected, "Home is the default tab")
        XCTAssertFalse(tabButton(app, "Yêu thích").isSelected)
        XCTAssertFalse(tabButton(app, "Tìm kiếm").isSelected)
        XCTAssertTrue(app.navigationBars["Khám phá"].waitForExistence(timeout: 5), "title 'Khám phá'")

        // No Radio in the chrome (D1). Podcast titles from the API may contain the word
        // ("RADIO NÔNG DÂN"), so only the tab bar and the navigation bars are checked.
        let radio = NSPredicate(format: "label CONTAINS[c] 'radio' OR identifier CONTAINS[c] 'radio'")
        XCTAssertEqual(tabBar.descendants(matching: .any).matching(radio).count, 0)
        XCTAssertEqual(app.navigationBars.descendants(matching: .any).matching(radio).count, 0)
        attachScreenshot("main-three-tabs")
    }

    @MainActor
    func testFavouriteEmptyStateDiscoverMore() {
        let app = launchMain()

        tabButton(app, "Yêu thích").tap()
        element(app, "favourite.root").waitToExist(timeout: 5)
        XCTAssertTrue(app.navigationBars["Yêu thích"].waitForExistence(timeout: 5), "title 'Yêu thích'")
        let empty = app.staticTexts["favourite.empty"]
        empty.waitToExist(timeout: 5)
        XCTAssertEqual(empty.label, "Không có chương trình yêu thích.")
        let discoverMore = app.buttons["favourite.discoverMore"]
        XCTAssertEqual(discoverMore.label, "Khám phá thêm")
        XCTAssertTrue(tabButton(app, "Yêu thích").isSelected)
        attachScreenshot("favourite-empty")

        discoverMore.tap()

        element(app, "home.root").waitToExist(timeout: 5, "'Khám phá thêm' must open Home")
        XCTAssertTrue(poll(timeout: 5) { self.tabButton(app, "Khám phá").isSelected })
        XCTAssertTrue(app.navigationBars["Khám phá"].exists)
    }

    @MainActor
    func testEachTabKeepsItsNavigationStack() {
        let app = launchMain()
        let firstTrending = app.buttons["home.section.trending.item.0"]
        firstTrending.waitToExist(timeout: Self.networkTimeout, "trending needs an item")

        firstTrending.tap()
        let detail = element(app, "detail.root")
        detail.waitToExist(timeout: 5, "a trending tap must push the detail stub")

        tabButton(app, "Tìm kiếm").tap()
        element(app, "search.root").waitToExist(timeout: 5)
        XCTAssertTrue(app.navigationBars["Tìm kiếm"].waitForExistence(timeout: 5), "title 'Tìm kiếm'")
        XCTAssertFalse(detail.exists, "Search has its own stack")

        tabButton(app, "Khám phá").tap()
        detail.waitToExist(timeout: 5, "Home must still show the pushed detail")
        attachScreenshot("home-stack-kept")

        app.navigationBars.buttons.element(boundBy: 0).tap()
        element(app, "home.root").waitToExist(timeout: 5)
        XCTAssertTrue(poll(timeout: 5) { !detail.exists }, "Back returns to Home")
        XCTAssertTrue(firstTrending.exists)
    }

    @MainActor
    func testMiniPlayerSlotSitsAboveTabBar() {
        let app = launchMain([.miniPlayerPlaceholder])
        let tabBar = app.tabBars.firstMatch

        for label in tabLabels {
            tabButton(app, label).tap()
            XCTAssertTrue(poll(timeout: 5) { self.tabButton(app, label).isSelected }, label)
            let slot = element(app, "miniplayer.slot")
            slot.waitToExist(timeout: 5, "slot on tab \(label)")
            XCTAssertGreaterThan(slot.frame.height, 0, label)
            XCTAssertLessThanOrEqual(
                slot.frame.maxY,
                tabBar.frame.minY + 1,
                "slot \(slot.frame) must sit above the tab bar \(tabBar.frame) on \(label)"
            )
            attachScreenshot("miniplayer-slot-\(label)")
        }
    }

    @MainActor
    func testNoMiniPlayerWhenNothingPlays() {
        let app = launchMain()

        for label in tabLabels {
            tabButton(app, label).tap()
            XCTAssertTrue(poll(timeout: 5) { self.tabButton(app, label).isSelected }, label)
            XCTAssertFalse(element(app, "miniplayer.slot").waitForExistence(timeout: 1), "no slot on \(label)")
        }
    }
}
