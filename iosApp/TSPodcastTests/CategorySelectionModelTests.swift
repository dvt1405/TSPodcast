import XCTest
@testable import TSPodcast

/// M1-AC12: selection rules of `SelectFavouriteCategoryScreen` and the shared E9 request.
@MainActor
final class CategorySelectionModelTests: XCTestCase {
    private let arts = CategoryItem(id: 1, name: "Arts")
    private let books = CategoryItem(id: 2, name: "Books")
    private let design = CategoryItem(id: 3, name: "Design")
    private let fashion = CategoryItem(id: 4, name: "Fashion")

    private func makeModel(
        categories: [CategoryItem]? = nil,
        save: @escaping @MainActor ([CategoryItem]) async throws -> Bool = { _ in true }
    ) -> CategorySelectionModel {
        let items = categories ?? [arts, books, design, fashion]
        return CategorySelectionModel(minimumSelection: 3, load: { items }, save: save)
    }

    func testStartsLoadingThenShowsTheList() async {
        let model = makeModel()
        XCTAssertEqual(model.state, .loading)

        await model.loadIfNeeded()

        XCTAssertEqual(model.state, .loaded([arts, books, design, fashion]))
    }

    func testSaveEnabledFromThreeAndDisabledAgainAfterRemoving() {
        let model = makeModel()
        XCTAssertFalse(model.canSave)
        model.toggle(arts)
        XCTAssertFalse(model.canSave)
        model.toggle(books)
        XCTAssertFalse(model.canSave)
        model.toggle(design)
        XCTAssertTrue(model.canSave)

        model.remove(books)
        XCTAssertFalse(model.canSave)
        XCTAssertEqual(model.selected, [arts, design])
    }

    func testBadgesKeepTheTapOrderAndATapUnselects() {
        let model = makeModel()
        model.toggle(design)
        model.toggle(arts)
        model.toggle(fashion)
        XCTAssertEqual(model.selected, [design, arts, fashion])
        XCTAssertTrue(model.isSelected(arts))

        model.toggle(arts)

        XCTAssertEqual(model.selected, [design, fashion])
        XCTAssertFalse(model.isSelected(arts))
    }

    func testSaveSendsTheSelection() async {
        var saved: [[CategoryItem]] = []
        let model = makeModel { selection in
            saved.append(selection)
            return true
        }
        [fashion, arts, books].forEach(model.toggle)

        let ok = await model.save()

        XCTAssertTrue(ok)
        XCTAssertEqual(saved, [[fashion, arts, books]])
    }

    func testSaveBelowTheMinimumDoesNothing() async {
        var calls = 0
        let model = makeModel { _ in
            calls += 1
            return true
        }
        [arts, books].forEach(model.toggle)

        let ok = await model.save()

        XCTAssertFalse(ok)
        XCTAssertEqual(calls, 0)
    }

    func testRefusedOrFailedSaveStaysOnTheScreen() async {
        struct SaveError: Error {}
        let refused = makeModel { _ in false }
        [arts, books, design].forEach(refused.toggle)
        let refusedResult = await refused.save()
        XCTAssertFalse(refusedResult)

        let failing = makeModel { _ in throw SaveError() }
        [arts, books, design].forEach(failing.toggle)
        let failedResult = await failing.save()
        XCTAssertFalse(failedResult)
        XCTAssertTrue(failing.canSave, "the user can try again")
    }

    func testCatalogSendsOneRequestForPrefetchAndLoad() async {
        var calls = 0
        let catalog = CategoryCatalog {
            calls += 1
            await Task.yield()
            return [self.arts]
        }

        catalog.prefetch()
        catalog.prefetch()
        let first = await catalog.categories()
        let second = await catalog.categories()

        XCTAssertEqual(calls, 1)
        XCTAssertEqual(first, [arts])
        XCTAssertEqual(second, [arts])
    }

    func testCatalogAsksAgainAfterAnEmptyResult() async {
        var calls = 0
        let catalog = CategoryCatalog {
            calls += 1
            return calls == 1 ? [] : [self.arts]
        }

        let first = await catalog.categories()
        let second = await catalog.categories()

        XCTAssertEqual(first, [])
        XCTAssertEqual(second, [arts])
        XCTAssertEqual(calls, 2)
    }
}
