import XCTest

/// M1-AC12, M1-AC13: favourite category selection (features-spec §3.2, Android
/// `SelectFavouriteCategoryScreen`) and the relaunch destination once it is saved.
final class CategorySelectionUITests: TSPodcastUITestCase {
    @MainActor
    func testSaveEnabledFromThreeCategories() {
        let app = launch([.reset])
        finishOnboarding(app)
        let save = app.buttons["category.save"]

        let arts = categoryCell(app, 1)
        XCTAssertEqual(arts.label, "Arts")
        XCTAssertFalse(save.isEnabled, "0 selected")
        attachScreenshot("categories-none-selected")

        arts.tap()
        XCTAssertFalse(save.isEnabled, "1 selected")
        XCTAssertTrue(element(app, "category.selected.1").waitForExistence(timeout: 2), "badge of Arts")

        categoryCell(app, 2).tap()
        XCTAssertFalse(save.isEnabled, "2 selected")

        categoryCell(app, 3).tap()
        XCTAssertTrue(save.isEnabled, "3 selected")
        XCTAssertTrue(categoryCell(app, 3).isSelected)
        attachScreenshot("categories-three-selected")

        app.buttons["category.selected.2.remove"].tap()
        XCTAssertFalse(element(app, "category.selected.2").waitForExistence(timeout: 1))
        XCTAssertFalse(save.isEnabled, "back to 2 after removing a badge")
        XCTAssertFalse(categoryCell(app, 2).isSelected)

        categoryCell(app, 2).tap()
        XCTAssertTrue(save.isEnabled, "3 selected again")
        save.tap()

        element(app, "home.root").waitToExist(timeout: 10, "saving must open Main")
        let discover = app.tabBars.buttons["Khám phá"]
        XCTAssertTrue(discover.exists)
        XCTAssertTrue(discover.isSelected)
        XCTAssertFalse(app.scrollViews["category.grid"].exists)
        attachScreenshot("categories-saved-home")
    }

    @MainActor
    func testFallsBackToBundledCategoriesWhenApiUnreachable() {
        let app = launch([.reset], environment: ["TSP_UITEST_BASE_URL": "https://nonexistent.invalid/api/1.0/"])
        finishOnboarding(app)

        XCTAssertEqual(categoryCell(app, 1).label, "Arts")
        XCTAssertEqual(categoryCell(app, 3).label, "Design")
        XCTAssertFalse(app.alerts.firstMatch.exists, "no blocking error")
        XCTAssertTrue(app.buttons["category.save"].exists)
        attachScreenshot("categories-bundled-fallback")
    }

    @MainActor
    func testRelaunchGoesStraightToHome() {
        var app = launch([.reset])
        finishOnboarding(app)
        for id in [1, 2, 3] {
            categoryCell(app, id).tap()
        }
        app.buttons["category.save"].tap()
        element(app, "home.root").waitToExist(timeout: 10, "saving must open Main")

        app.terminate()
        app = launch()

        element(app, "home.root").waitToExist(timeout: 10, "a saved selection must open Home on relaunch")
        XCTAssertFalse(app.otherElements["onboarding.page.0"].exists)
        XCTAssertFalse(app.scrollViews["category.grid"].exists)
        XCTAssertTrue(app.tabBars.buttons["Khám phá"].isSelected)
        attachScreenshot("relaunch-home")
    }
}
