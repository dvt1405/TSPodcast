import XCTest
@testable import TSPodcast

/// M6 §3.4: a news list is saved show by show, the started episode's show last (it sets the
/// current id). Pure Swift, also run with `swift test` on macOS.
final class NewsHistoryOrderTests: XCTestCase {
    func testOtherShowsFirstInOrderThenTheStartedOne() {
        let groups = NewsHistoryOrder.groups(feedIds: [7, 8, 9, 7, 8], startIndex: 1)
        XCTAssertEqual(groups, [
            .init(feedId: 7, indices: [0, 3]),
            .init(feedId: 9, indices: [2]),
            .init(feedId: 8, indices: [1, 4]),
        ])
    }

    func testSingleShow() {
        XCTAssertEqual(NewsHistoryOrder.groups(feedIds: [5, 5], startIndex: 0), [.init(feedId: 5, indices: [0, 1])])
    }

    func testOutOfRangeStartSavesNothing() {
        XCTAssertEqual(NewsHistoryOrder.groups(feedIds: [1, 2], startIndex: 2), [])
        XCTAssertEqual(NewsHistoryOrder.groups(feedIds: [], startIndex: 0), [])
    }
}
