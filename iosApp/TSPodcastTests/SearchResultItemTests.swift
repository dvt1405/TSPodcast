import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M2-AC12: a search row (Android `SearchPodcastItem`): "author · up to 3 categories", cover
/// `image` else `artwork`, description as plain text.
final class SearchResultItemTests: XCTestCase {
    private let route = PodcastRoute(feedId: 9, title: "T", summary: "", imageURL: nil)

    private func item(
        author: String = "A",
        categories: [String: String] = [:],
        image: String = "https://img.example.com/i.jpg",
        artwork: String = "https://img.example.com/a.jpg",
        summary: String = ""
    ) -> SearchResultItem {
        SearchResultItem(
            id: 9,
            title: "T",
            author: author,
            categories: categories,
            image: image,
            artwork: artwork,
            summary: summary,
            route: route
        )
    }

    func testAuthorThenAtMostThreeCategoriesInIdOrder() {
        let row = item(categories: ["4": "Food", "2": "Books", "1": "Arts", "3": "Design"])

        XCTAssertEqual(row.subtitle, "A · Arts · Books · Design")
    }

    func testIdsSortNumericallyNotAsText() {
        let row = item(categories: ["10": "Ten", "9": "Nine"])

        XCTAssertEqual(row.subtitle, "A · Nine · Ten")
    }

    func testNoCategoryIsTheAuthorOnly() {
        XCTAssertEqual(item(categories: [:]).subtitle, "A")
    }

    func testFewerCategoriesLeaveNoTrailingSeparator() {
        XCTAssertEqual(item(categories: ["1": "Arts"]).subtitle, "A · Arts")
    }

    func testBlankAuthorIsLeftOut() {
        XCTAssertEqual(item(author: "  ", categories: ["1": "Arts"]).subtitle, "Arts")
        XCTAssertEqual(item(author: "", categories: [:]).subtitle, "")
    }

    func testImageThenArtwork() {
        XCTAssertEqual(item().imageURL, "https://img.example.com/i.jpg")
        XCTAssertEqual(item(image: " ").imageURL, "https://img.example.com/a.jpg")
        XCTAssertNil(item(image: "", artwork: "").imageURL)
    }

    func testKotlinFeedMapsHtmlDescriptionToPlainTextAndRoute() {
        let feed = Feed(
            artwork: "https://img.example.com/a.jpg", author: "VTV", categories: ["55": "News", "1": "Arts"],
            contentType: "", crawlErrors: 0, dead: 0, description: "<p>A &amp; B</p>", episodeCount: 3,
            explicit: false, generator: "", id: 77, image: "", imageUrlHash: 0, itunesId: nil, language: "vi",
            lastCrawlTime: 0, lastGoodHttpStatusTime: 0, lastHttpStatus: 200, lastParseTime: 0, lastUpdateTime: 0,
            link: "", locked: 0, medium: "podcast", newestItemPubdate: 0, originalUrl: "", ownerName: "",
            parseErrors: 0, podcastGuid: "", title: "Tin tức", type: 0, url: "", chash: nil, funding: nil,
            itunesType: nil, value: nil
        )

        let row = SearchResultItem(feed)

        XCTAssertEqual(row.id, 77)
        XCTAssertEqual(row.title, "Tin tức")
        XCTAssertEqual(row.subtitle, "VTV · Arts · News")
        XCTAssertEqual(row.summary, "A & B")
        XCTAssertEqual(row.imageURL, "https://img.example.com/a.jpg", "blank image falls back to artwork")
        XCTAssertEqual(row.route.feedId, 77)
        XCTAssertEqual(row.route.title, "Tin tức")
        XCTAssertEqual(row.route.summary, "A & B")
        XCTAssertEqual(row.route.imageURL, "https://img.example.com/a.jpg", "Podcast.fromFeed: cover = artwork")
    }
}
