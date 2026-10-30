import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M6-AC17 (hub screen), AC19 (`news_hub_open`, `news_brief_play`): what each tap of the hub
/// plays, the night shelf timer and toast, and the open event.
@MainActor
final class NewsHubModelTests: XCTestCase {
    private func makeModel(
        entry: NewsHubEntry = .seeAll,
        initial: NewsHubContent? = nil,
        source: FakeNewsHubDataSource = FakeNewsHubDataSource(),
        playback: RecordingNewsHubPlayback = RecordingNewsHubPlayback(),
        analytics: RecordingAnalyticsTracker = RecordingAnalyticsTracker(),
        toastDuration: Duration = .seconds(60)
    ) -> NewsHubModel {
        NewsHubModel(
            entry: entry,
            initial: initial,
            dataSource: source,
            playback: playback,
            analytics: analytics,
            toastDuration: toastDuration
        )
    }

    func testOpeningLogsTheEntryOnceAndLoadsOnce() async {
        let source = FakeNewsHubDataSource()
        let analytics = RecordingAnalyticsTracker()
        let model = makeModel(entry: .brief, source: source, analytics: analytics)
        XCTAssertEqual(model.state, .loading)

        await model.loadIfNeeded()
        await model.loadIfNeeded()

        XCTAssertEqual(model.state, .loaded(.sample))
        XCTAssertEqual(source.loads, 1)
        let opens = analytics.events.filter { $0.name == AnalyticsEvents.shared.NEWS_HUB_OPEN }
        XCTAssertEqual(opens.count, 1)
        XCTAssertEqual(opens.first?.params, [AnalyticsEvents.shared.PARAM_FROM: "home_brief"])
        XCTAssertEqual(opens.first?.screen, NewsHubModel.analyticsScreen)
        XCTAssertEqual(NewsHubEntry.seeAll.rawValue, "home_seeAll")
    }

    func testContentHandedOverByHomeIsShownWithoutALoad() async {
        let source = FakeNewsHubDataSource()
        let analytics = RecordingAnalyticsTracker()
        let model = makeModel(initial: .sample, source: source, analytics: analytics)
        XCTAssertEqual(model.state, .loaded(.sample))

        await model.loadIfNeeded()

        XCTAssertEqual(source.loads, 0)
        XCTAssertEqual(analytics.events.map(\.name), [AnalyticsEvents.shared.NEWS_HUB_OPEN], "still counted as an open")
    }

    func testFailureThenRetry() async {
        let source = FakeNewsHubDataSource(result: .failure(.network))
        let model = makeModel(source: source)

        await model.loadIfNeeded()
        XCTAssertEqual(model.state, .failed)

        source.setResult(.success(.sample))
        await model.retry()
        XCTAssertEqual(model.state, .loaded(.sample))
    }

    func testFailedRefreshKeepsTheContent() async {
        let source = FakeNewsHubDataSource(result: .failure(.network))
        let model = makeModel(initial: .sample, source: source)

        await model.retry()

        XCTAssertEqual(model.state, .loaded(.sample))
    }

    func testHubTurnedOffOrEmptyIsUnavailable() async {
        let off = FakeNewsHubDataSource(isEnabled: false)
        let offModel = makeModel(source: off)
        await offModel.loadIfNeeded()
        XCTAssertEqual(offModel.state, .unavailable)
        XCTAssertEqual(off.loads, 0, "no request while the hub is off")

        let empty = FakeNewsHubDataSource(result: .success(nil))
        let emptyModel = makeModel(source: empty)
        await emptyModel.loadIfNeeded()
        XCTAssertEqual(emptyModel.state, .unavailable)
    }

    func testPlayAllPlaysTheBriefInOrderWithAutoAdvance() async {
        let playback = RecordingNewsHubPlayback()
        let analytics = RecordingAnalyticsTracker()
        let model = makeModel(initial: .sample, playback: playback, analytics: analytics)

        model.playBrief()

        let play = try? XCTUnwrap(playback.plays.last)
        XCTAssertEqual(playback.plays.count, 1)
        XCTAssertEqual(play?.request.items.map(\.id), ["1", "2", "3", "4", "5"])
        XCTAssertEqual(play?.request.startIndex, 0)
        XCTAssertEqual(play?.request.autoAdvance, true)
        XCTAssertEqual(play?.presentsPlayer, true)
        XCTAssertEqual(play?.request.items.first?.podcastTitle, "Show 7")
        XCTAssertEqual(play?.request.items.first?.artworkURL, "https://img.example.com/feed7.jpg", "the feed's own cover")
        let brief = analytics.events.filter { $0.name == AnalyticsEvents.shared.NEWS_BRIEF_PLAY }
        XCTAssertEqual(brief.count, 1)
        XCTAssertEqual(brief.first?.params, [AnalyticsEvents.shared.PARAM_COUNT: "5"])
    }

    func testEmptyBriefPlaysNothing() {
        var content = NewsHubContent.sample
        content.brief = []
        let playback = RecordingNewsHubPlayback()
        let analytics = RecordingAnalyticsTracker()
        let model = makeModel(initial: content, playback: playback, analytics: analytics)

        model.playBrief()

        XCTAssertTrue(playback.plays.isEmpty)
        XCTAssertTrue(analytics.events.isEmpty)
    }

    func testLatestTapPlaysTheLatestListWithoutAutoAdvance() {
        let playback = RecordingNewsHubPlayback()
        let model = makeModel(initial: .sample, playback: playback)

        model.playLatest(at: 2)

        let play = playback.plays.last
        XCTAssertEqual(play?.request.items.map(\.id), ["11", "12", "13", "14", "15", "16"])
        XCTAssertEqual(play?.request.startIndex, 2)
        XCTAssertEqual(play?.request.autoAdvance, false)
        XCTAssertEqual(play?.presentsPlayer, true)
        XCTAssertTrue(playback.timers.isEmpty)
    }

    func testNightTapPlaysSetsThirtyMinutesAndShowsTheToast() {
        let playback = RecordingNewsHubPlayback()
        let model = makeModel(initial: .sample, playback: playback)

        model.playNight(at: 1)

        let play = playback.plays.last
        XCTAssertEqual(play?.request.items.map(\.id), ["21", "22"])
        XCTAssertEqual(play?.request.startIndex, 1)
        XCTAssertEqual(play?.request.autoAdvance, false)
        XCTAssertEqual(play?.presentsPlayer, false, "the toast stays visible on the hub")
        XCTAssertEqual(playback.timers.map(\.setting), [.minutes(30)])
        XCTAssertEqual(playback.timers.map(\.origin), [AnalyticsEvents.shared.ORIGIN_NIGHT_SHELF])
        XCTAssertEqual(model.toastMinutes, 30)

        model.dismissToast()
        XCTAssertNil(model.toastMinutes)
    }

    func testToastHidesItself() async throws {
        let model = makeModel(initial: .sample, toastDuration: .milliseconds(50))

        model.playNight(at: 0)
        XCTAssertEqual(model.toastMinutes, 30)

        let deadline = Date().addingTimeInterval(2)
        while model.toastMinutes != nil, Date() < deadline {
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertNil(model.toastMinutes)
    }

    func testOutOfRangeTapsDoNothing() {
        let playback = RecordingNewsHubPlayback()
        let model = makeModel(initial: .sample, playback: playback)

        model.playLatest(at: 99)
        model.playNight(at: -1)

        XCTAssertTrue(playback.plays.isEmpty)
        XCTAssertNil(model.toastMinutes)
    }

    func testSourceOpensTheOrdinaryPodcastDetail() {
        let source = NewsHubContent.sample.sources[0]
        XCTAssertEqual(DiscoverRoute.podcast(source.route).podcast?.feedId, 7)
        XCTAssertEqual(source.route.title, "Thời sự VOV")
        XCTAssertNil(source.route.transitionSourceID)
    }

    func testHidingAShowRemovesItEverywhere() {
        let model = makeModel(initial: .sample)

        model.removeShow(feedId: 7)

        let content = try? XCTUnwrap(model.content)
        XCTAssertFalse(content?.brief.contains { $0.feedId == 7 } ?? true)
        XCTAssertFalse(content?.latest.contains { $0.feedId == 7 } ?? true)
        XCTAssertEqual(content?.sources.map(\.id), [8])

        model.removeShow(feedId: 8)
        XCTAssertEqual(model.state, .unavailable, "no news left: nothing to lead with")
    }

    /// Only "Phát tất cả" of the brief asks for auto-advance (M6-AC4).
    func testNewsRequestDefaultsToNoAutoAdvance() {
        let request = PlaybackRequest(news: NewsHubContent.sample.latest, startIndex: 0)
        XCTAssertFalse(request.autoAdvance)
        XCTAssertNil(request.history, "fakes carry no Kotlin model")
    }
}
