import XCTest
@testable import TSPodcast

/// M2-AC14: the category list (features-spec §3.9 [Sửa]) loads E1 for its category once, and
/// has its own loading, loaded, empty and failed states with "Tải lại".
@MainActor
final class CategoryPodcastsModelTests: XCTestCase {
    private let arts = CategoryItem(id: 1, name: "Arts")

    private func card(_ id: Int64) -> PodcastCard {
        PodcastCard(id: id, title: "Podcast \(id)", summary: "About \(id)", imageURL: nil)
    }

    func testStartsLoadingThenShowsThePodcastsInOrder() async {
        let source = FakeSearchDataSource(categoryResults: .success([card(1), card(2)]))
        let model = CategoryPodcastsModel(category: arts, dataSource: source)

        XCTAssertEqual(model.state, .loading)
        await model.loadIfNeeded()

        XCTAssertEqual(model.state, .loaded([card(1), card(2)]))
        XCTAssertEqual(source.categoryRequests, [1], "asks for its own category")
        XCTAssertEqual(model.category.name, "Arts")
    }

    func testNoPodcastIsEmpty() async {
        let model = CategoryPodcastsModel(category: arts, dataSource: FakeSearchDataSource(categoryResults: .success([])))

        await model.loadIfNeeded()

        XCTAssertEqual(model.state, .empty)
    }

    func testFailureThenRetryAsksTheSameCategoryAgain() async {
        let source = FakeSearchDataSource(categoryResults: .failure(.network), .success([card(7)]))
        let model = CategoryPodcastsModel(category: CategoryItem(id: 55, name: "News"), dataSource: source)

        await model.loadIfNeeded()
        XCTAssertEqual(model.state, .failed)

        await model.retry()

        XCTAssertEqual(model.state, .loaded([card(7)]))
        XCTAssertEqual(source.categoryRequests, [55, 55])
    }

    func testLoadsOnlyOnce() async {
        let source = FakeSearchDataSource(categoryResults: .success([card(1)]))
        let model = CategoryPodcastsModel(category: arts, dataSource: source)

        await model.loadIfNeeded()
        await model.loadIfNeeded()

        XCTAssertEqual(source.categoryRequests, [1])
    }
}
