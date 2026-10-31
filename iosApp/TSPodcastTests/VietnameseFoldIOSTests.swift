import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M6-AC8 on the device: the iOS `actual` of the shared fold (`decomposedStringWithCanonicalMapping`)
/// gives the same results as the JVM one (`VietnameseFoldTest`), and the shared filter runs on it.
final class VietnameseFoldIOSTests: XCTestCase {
    private func fold(_ text: String) -> String {
        VietnameseFold.shared.fold(text: text)
    }

    func testSameCasesAsTheSharedTest() {
        XCTAssertEqual(fold("Đọc truyện"), "doc truyen")
        XCTAssertEqual(fold("ĐÀI"), "dai")
        XCTAssertEqual(fold("Thời sự 6h"), "thoi su 6h")
        XCTAssertEqual(fold("  Kinh   tế "), "kinh te")
        XCTAssertEqual(fold("NGƯỜI"), "nguoi")
        XCTAssertEqual(fold("\u{0110}o\u{0323}c truye\u{0302}\u{0323}n"), "doc truyen", "already decomposed")
    }

    func testEpisodeFilterMatchesWithoutAccents() {
        let episodes = [
            SharedModelFactory.episode(1, title: "Đọc truyện đêm khuya"),
            SharedModelFactory.episode(2, title: "Thời sự 6h"),
        ]
        let filter = EpisodeFilter.companion.index(episodes: episodes)

        XCTAssertEqual(filter.matchingIndices(query: "doc truyen").map(\.int32Value), [0])
        XCTAssertEqual(filter.matchingIndices(query: "THOI SU").map(\.int32Value), [1])
        XCTAssertEqual(filter.matchingIndices(query: "").count, 2)
    }
}
