import XCTest
@testable import TSPodcast

/// Share links match Android `WebLinks` and are accepted by the app's own deep-link parser.
final class ShareLinksTests: XCTestCase {
    func testPodcastAndEpisodeLinks() {
        XCTAssertEqual(ShareLinks.podcast(id: 42, title: "Tiếng Việt mỗi ngày").absoluteString,
                       "https://tspodcast.web.app/podcast/42-tieng-viet-moi-ngay")
        XCTAssertEqual(ShareLinks.episode(id: 7, title: "Đầu đồng xu").absoluteString,
                       "https://tspodcast.web.app/episode/7-dau-dong-xu")
    }

    func testSlugFallbackAndLength() {
        XCTAssertEqual(ShareLinks.slugify("  "), "podcast")
        XCTAssertEqual(ShareLinks.slugify(nil), "podcast")
        XCTAssertEqual(ShareLinks.slugify("A -- B!!"), "a-b")
        let long = ShareLinks.slugify(String(repeating: "ab ", count: 60))
        XCTAssertLessThanOrEqual(long.count, 80)
        XCTAssertFalse(long.hasSuffix("-"))
    }

    func testShareTextContainsTheLink() {
        let url = ShareLinks.podcast(id: 1, title: "X")
        XCTAssertTrue(ShareLinks.podcastText(title: "X", url: url).contains(url.absoluteString))
    }
}
