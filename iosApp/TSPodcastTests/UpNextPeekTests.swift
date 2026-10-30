import XCTest
@testable import TSPodcast

/// M5-AC4: the peek rows under the player controls (current + next when it fits; live 1 row).
final class UpNextPeekTests: XCTestCase {
    func testCurrentAndNext() {
        XCTAssertEqual(UpNextPeek.rows(queueCount: 5, currentIndex: 0, maxRows: 2), [0, 1])
        XCTAssertEqual(UpNextPeek.rows(queueCount: 5, currentIndex: 2, maxRows: 2), [2, 3])
    }

    func testLastItemShowsOneRow() {
        XCTAssertEqual(UpNextPeek.rows(queueCount: 5, currentIndex: 4, maxRows: 2), [4])
    }

    func testLiveSingleItemShowsOneRow() {
        XCTAssertEqual(UpNextPeek.rows(queueCount: 1, currentIndex: 0, maxRows: 2), [0])
    }

    func testOneRowWhenSpaceIsTight() {
        XCTAssertEqual(UpNextPeek.rows(queueCount: 5, currentIndex: 2, maxRows: 1), [2])
    }

    func testNeverMoreThanCurrentAndNext() {
        XCTAssertEqual(UpNextPeek.rows(queueCount: 5, currentIndex: 0, maxRows: 4), [0, 1])
    }

    func testNothingForAnEmptyQueueOrABadIndex() {
        XCTAssertEqual(UpNextPeek.rows(queueCount: 0, currentIndex: 0, maxRows: 2), [])
        XCTAssertEqual(UpNextPeek.rows(queueCount: 3, currentIndex: 5, maxRows: 2), [])
        XCTAssertEqual(UpNextPeek.rows(queueCount: 3, currentIndex: 3, maxRows: 2), [])
        XCTAssertEqual(UpNextPeek.rows(queueCount: 3, currentIndex: -1, maxRows: 2), [])
        XCTAssertEqual(UpNextPeek.rows(queueCount: 3, currentIndex: 0, maxRows: 0), [])
    }

    func testMaxRows() {
        XCTAssertEqual(UpNextPeek.maxRows(isAccessibilitySize: false, isCompactHeight: false), 2)
        XCTAssertEqual(UpNextPeek.maxRows(isAccessibilitySize: true, isCompactHeight: false), 1)
        XCTAssertEqual(UpNextPeek.maxRows(isAccessibilitySize: false, isCompactHeight: true), 1)
        XCTAssertEqual(UpNextPeek.maxRows(isAccessibilitySize: true, isCompactHeight: true), 1)
    }
}
