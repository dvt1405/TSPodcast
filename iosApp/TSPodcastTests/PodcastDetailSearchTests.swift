import XCTest
@testable import TSPodcast

/// M6-AC9: "Tìm trong show" on Podcast detail: shown above 20 episodes, the 1 000-episode index
/// loaded once by the first query, 200 ms debounce, empty state, footnote, results played as a list.
@MainActor
final class PodcastDetailSearchTests: XCTestCase {
    private var clock: TestClock!

    override func setUp() async throws {
        clock = TestClock()
    }

    private func rows(_ count: Int) -> [EpisodeRow] {
        (1...Int64(count)).map(EpisodeRow.sample)
    }

    private func makeModel(
        _ source: FakePodcastDetailDataSource,
        playback: RecordingPlaybackController = RecordingPlaybackController(),
        analytics: RecordingAnalyticsTracker = RecordingAnalyticsTracker()
    ) -> PodcastDetailModel {
        PodcastDetailModel(podcast: .sample, dataSource: source, playback: playback, analytics: analytics, clock: clock, debounce: .milliseconds(200))
    }

    private func type(_ text: String, into model: PodcastDetailModel) async {
        model.searchQueryChanged(text)
        await clock.advance(by: .milliseconds(200))
        for _ in 0..<400 where model.searchState == .loading {
            try? await Task.sleep(for: .milliseconds(5))
        }
    }

    func testSearchShowsAboveTwentyEpisodes() async {
        let twenty = makeModel(FakePodcastDetailDataSource(.success(PodcastDetailContent(episodes: rows(20)))))
        await twenty.loadIfNeeded()
        XCTAssertFalse(twenty.isSearchAvailable)

        let more = makeModel(FakePodcastDetailDataSource(.success(PodcastDetailContent(episodes: rows(21)))))
        await more.loadIfNeeded()
        XCTAssertTrue(more.isSearchAvailable)

        let announced = makeModel(FakePodcastDetailDataSource(.success(PodcastDetailContent(episodes: rows(5), feedEpisodeCount: 25))))
        await announced.loadIfNeeded()
        XCTAssertTrue(announced.isSearchAvailable, "E11 says the show has 25")

        let hidden = makeModel(FakePodcastDetailDataSource(.success(PodcastDetailContent(episodes: [], isExplicitFeedHidden: true, feedEpisodeCount: 50))))
        await hidden.loadIfNeeded()
        XCTAssertFalse(hidden.isSearchAvailable, "nothing to search in a feed hidden by D6")
    }

    func testFirstQueryLoadsTheIndexOnceAndLaterQueriesReuseIt() async {
        let source = FakePodcastDetailDataSource(.success(PodcastDetailContent(episodes: rows(30))))
        source.searchResult = ShowSearchIndexResult(index: FakeShowSearchIndex(rows: rows(30)), isFallback: false)
        let analytics = RecordingAnalyticsTracker()
        let model = makeModel(source, analytics: analytics)
        await model.loadIfNeeded()

        await type("episode 1", into: model)
        await type("episode 2", into: model)
        await type("episode 3", into: model)

        XCTAssertEqual(source.searchRequests, [42], "one index per Detail screen")
        XCTAssertEqual(source.requests, [42], "the Detail list is not loaded again")
        guard case .results(let found) = model.searchState else { return XCTFail("\(model.searchState)") }
        XCTAssertEqual(found.map(\.id), [3, 13, 23, 30])
        XCTAssertEqual(analytics.events.map(\.name), ["show_search"], "once per screen")
        XCTAssertEqual(analytics.events.first?.params, ["result_count": "12", "truncated": "false"], "never the query")
    }

    func testTypingFastFiltersOnceAfterTheDebounce() async {
        let source = FakePodcastDetailDataSource(.success(PodcastDetailContent(episodes: rows(30))))
        let model = makeModel(source)
        await model.loadIfNeeded()

        model.searchQueryChanged("e")
        model.searchQueryChanged("ep")
        XCTAssertEqual(model.searchState, .loading)
        XCTAssertTrue(source.searchRequests.isEmpty, "nothing before 200 ms")
        await type("episode 30", into: model)

        XCTAssertEqual(model.searchState, .results([.sample(30)]))
        XCTAssertEqual(source.searchRequests.count, 1)
    }

    func testNoMatchAndBlankQuery() async {
        let model = makeModel(FakePodcastDetailDataSource(.success(PodcastDetailContent(episodes: rows(30)))))
        await model.loadIfNeeded()

        await type("bóng đá", into: model)
        XCTAssertEqual(model.searchState, .noMatch)

        model.searchQueryChanged("   ")
        XCTAssertEqual(model.searchState, .idle, "back to the list at once")
        XCTAssertEqual(model.episodes.count, 30)
    }

    func testTruncatedIndexHasAFootnoteAndFallbackIsFlagged() async {
        let source = FakePodcastDetailDataSource(.success(PodcastDetailContent(episodes: rows(30))))
        source.searchResult = ShowSearchIndexResult(
            index: FakeShowSearchIndex(rows: rows(30), loadedCount: 1_000, isTruncated: true),
            isFallback: false
        )
        let model = makeModel(source)
        await model.loadIfNeeded()

        await type("episode", into: model)
        XCTAssertEqual(model.searchFootnoteCount, 1_000)
        XCTAssertFalse(model.isSearchingOnScreenOnly)

        let failing = makeModel(FakePodcastDetailDataSource(.success(PodcastDetailContent(episodes: rows(30)))))
        await failing.loadIfNeeded()
        await type("episode 2", into: failing)
        XCTAssertTrue(failing.isSearchingOnScreenOnly, "the 1 000-episode request failed: on-screen episodes only")
        XCTAssertNil(failing.searchFootnoteCount)
        XCTAssertEqual(failing.searchState, .results([.sample(2), .sample(12), .sample(20), .sample(21), .sample(22), .sample(23), .sample(24), .sample(25), .sample(26), .sample(27), .sample(28), .sample(29)]))
    }

    func testTapOnAResultPlaysTheResultsList() async {
        let playback = RecordingPlaybackController()
        let model = makeModel(FakePodcastDetailDataSource(.success(PodcastDetailContent(episodes: rows(30)))), playback: playback)
        await model.loadIfNeeded()
        await type("episode 1", into: model)

        XCTAssertTrue(model.playSearchResult(index: 2))

        let request = try? XCTUnwrap(playback.requests.last)
        XCTAssertEqual(request?.items.map(\.id), ["1", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "21"])
        XCTAssertEqual(request?.startIndex, 2)
        XCTAssertEqual(request?.autoAdvance, false)
        XCTAssertFalse(model.playSearchResult(index: 99))
    }
}
