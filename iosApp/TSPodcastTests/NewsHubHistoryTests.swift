import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M6 §3.4: a play from the news hub saves every episode of the list with the podcast of its
/// own feed (several shows in one list), and the current id is the started episode, so
/// "Tiếp tục nghe" and Favourite resolve any of them later.
@MainActor
final class NewsHubHistoryTests: XCTestCase {
    func testBriefPlaySavesEachShowWithItsOwnPodcast() async throws {
        let fixture = SharedGraphFixture()
        defer { fixture.close() }
        let vov = SharedModelFactory.feed(7, title: "Thời sự VOV")
        let vnexpress = SharedModelFactory.feed(8, title: "VnExpress hôm nay")
        let items = [
            SharedModelFactory.newsEpisode(101, feed: vov),
            SharedModelFactory.newsEpisode(201, feed: vnexpress),
            SharedModelFactory.newsEpisode(102, feed: vov),
        ]

        try await fixture.graph.playbackHistory.record(.news(items), itemId: "201")

        let currentId = try await fixture.graph.playbackHistory.currentItemId()
        XCTAssertEqual(currentId, "201")
        let storage = fixture.graph.podcastStorage
        let vovPodcast = try await storage.podcastById(id: 7)
        let vnexpressPodcast = try await storage.podcastById(id: 8)
        XCTAssertEqual(vovPodcast?.title, "Thời sự VOV")
        XCTAssertEqual(vnexpressPodcast?.title, "VnExpress hôm nay")
        for id: Int64 in [101, 102, 201] {
            let episode = try await storage.episodeById(id: id)
            XCTAssertNotNil(episode, "episode \(id)")
        }
        let vovEpisodes = try await storage.podcastWithEpisodes(id: 7)
        XCTAssertEqual(Set(vovEpisodes?.episodes.map(\.id) ?? []), [101, 102])
    }

    func testRequestCarriesTheSharedModels() {
        let feed = SharedModelFactory.feed(7)
        let news = NewsEpisode(sourceKey: "vov_thoisu", feed: feed, episode: SharedModelFactory.episode(101, feedId: 7))
        let item = NewsItem(news)

        let request = PlaybackRequest(news: [item], startIndex: 0, autoAdvance: true)

        XCTAssertEqual(request.history, .news([news]))
        XCTAssertEqual(request.items.first?.podcastTitle, "Feed 7")
        XCTAssertEqual(request.items.first?.artworkURL, "https://img.example.com/feed7.jpg", "the feed's cover, not the episode image")
        XCTAssertEqual(request.items.first?.url, "https://media.example.com/e101.mp3")
        XCTAssertEqual(item.durationSeconds, 1800)
    }
}
