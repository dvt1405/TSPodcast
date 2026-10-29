import XCTest
@testable import TSPodcast

/// M1-AC16: "Live Podcasts" pages of 3 rows (Android `liveEpisode.chunked(3)`).
@MainActor
final class LivePagingTests: XCTestCase {
    func testSevenItemsGiveThreeThreeOne() {
        let items = (1...7).map(LiveRowItem.sample)

        let pages = LivePaging.pages(items)

        XCTAssertEqual(pages.map(\.count), [3, 3, 1])
        XCTAssertEqual(pages.flatMap { $0 }, items, "order kept")
    }

    func testExactMultipleAndSmallLists() {
        XCTAssertEqual(LivePaging.pages((1...6).map(LiveRowItem.sample)).map(\.count), [3, 3])
        XCTAssertEqual(LivePaging.pages([.sample(1)]).map(\.count), [1])
        XCTAssertEqual(LivePaging.pages([]), [])
    }

    func testNoItemsIsEmptyState() {
        XCTAssertEqual(LivePaging.state(.success([])), .empty)
        XCTAssertEqual(LivePaging.state(.failure(.network)), .failed)
        XCTAssertEqual(LivePaging.state(.success([.sample(1)])), .loaded([[.sample(1)]]))
    }

    func testModelPagesLiveAndShowsEmpty() async {
        let paged = HomeModel(dataSource: FakeHomeDataSource(live: .success((1...7).map(LiveRowItem.sample))))
        await paged.loadIfNeeded()
        guard case .loaded(let pages) = paged.live else {
            return XCTFail("live not loaded: \(paged.live)")
        }
        XCTAssertEqual(pages.map(\.count), [3, 3, 1])

        let empty = HomeModel(dataSource: FakeHomeDataSource(live: .success([])))
        await empty.loadIfNeeded()
        XCTAssertEqual(empty.live, .empty)
    }
}
