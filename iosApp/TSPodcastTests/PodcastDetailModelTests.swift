import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M2-AC4: states, alert, inline retry, single load and the playlist handed to the player seam
/// (`PodcastViewModel` with the features-spec §3.5 [Sửa]). M4: offline banner and the Room
/// history of a tap.
@MainActor
final class PodcastDetailModelTests: XCTestCase {
    private let threeEpisodes = PodcastDetailContent(episodes: [.sample(1), .sample(2), .sample(3)])

    private func makeModel(
        _ source: FakePodcastDetailDataSource,
        playback: RecordingPlaybackController = RecordingPlaybackController()
    ) -> PodcastDetailModel {
        PodcastDetailModel(podcast: .sample, dataSource: source, playback: playback)
    }

    /// Lets queued tasks run until `condition` holds (at most ~1 s).
    private func waitUntil(_ condition: () -> Bool) async {
        for _ in 0..<1000 where !condition() {
            await Task.yield()
        }
    }

    func testStartsLoadingThenShowsEpisodesInOrder() async {
        let source = FakePodcastDetailDataSource(.success(threeEpisodes))
        let model = makeModel(source)

        XCTAssertEqual(model.state, .loading)
        await model.loadIfNeeded()

        XCTAssertEqual(model.state, .loaded([.sample(1), .sample(2), .sample(3)]))
        XCTAssertEqual(model.episodes.map(\.id), [1, 2, 3])
        XCTAssertFalse(model.isShowingErrorAlert)
        XCTAssertEqual(source.requests, [42], "loads the route's feed id")
    }

    func testStaysLoadingWhileTheRequestRuns() async {
        let source = FakePodcastDetailDataSource(.success(threeEpisodes))
        source.hold()
        let model = makeModel(source)

        let load = Task { await model.loadIfNeeded() }
        await waitUntil { source.heldCount == 1 }
        XCTAssertEqual(model.state, .loading)

        source.releaseAll()
        await load.value
        XCTAssertEqual(model.episodes.count, 3)
    }

    func testNoEpisodeGivesEmpty() async {
        let model = makeModel(FakePodcastDetailDataSource(.success(PodcastDetailContent(episodes: []))))

        await model.loadIfNeeded()

        XCTAssertEqual(model.state, .empty)
    }

    func testExplicitFeedGivesExplicitHidden() async {
        let content = PodcastDetailContent(episodes: [], isExplicitFeedHidden: true, hiddenExplicitCount: 7)
        let model = makeModel(FakePodcastDetailDataSource(.success(content)))

        await model.loadIfNeeded()

        XCTAssertEqual(model.state, .explicitHidden)
    }

    func testFailureShowsAlertAndDismissKeepsFailed() async {
        let model = makeModel(FakePodcastDetailDataSource(.failure(.network)))

        await model.loadIfNeeded()
        XCTAssertEqual(model.state, .failed)
        XCTAssertTrue(model.isShowingErrorAlert)

        model.dismissError()
        XCTAssertFalse(model.isShowingErrorAlert)
        XCTAssertEqual(model.state, .failed, "no endless shimmer after 'Huỷ'")
    }

    func testRetryGoesThroughLoadingToTheNewResult() async {
        let source = FakePodcastDetailDataSource(.failure(.network), .success(threeEpisodes))
        let model = makeModel(source)
        await model.loadIfNeeded()
        model.dismissError()

        source.hold()
        let retry = Task { await model.retry() }
        await waitUntil { source.heldCount == 1 }
        XCTAssertEqual(model.state, .loading)
        XCTAssertFalse(model.isShowingErrorAlert)

        source.releaseAll()
        await retry.value
        XCTAssertEqual(model.state, .loaded([.sample(1), .sample(2), .sample(3)]))
        XCTAssertEqual(source.requests.count, 2)
    }

    func testRetryThatFailsAgainShowsTheAlertAgain() async {
        let model = makeModel(FakePodcastDetailDataSource(.failure(.network)))
        await model.loadIfNeeded()
        model.dismissError()

        await model.retry()

        XCTAssertEqual(model.state, .failed)
        XCTAssertTrue(model.isShowingErrorAlert)
    }

    func testLoadIfNeededLoadsOnce() async {
        let source = FakePodcastDetailDataSource(.success(threeEpisodes))
        let model = makeModel(source)

        await model.loadIfNeeded()
        await model.loadIfNeeded()

        XCTAssertEqual(source.requests.count, 1)
    }

    func testPlaySendsTheWholePlaylistAndStartIndex() async {
        let playback = RecordingPlaybackController()
        let model = makeModel(FakePodcastDetailDataSource(.success(threeEpisodes)), playback: playback)
        await model.loadIfNeeded()

        XCTAssertTrue(model.play(index: 2))

        XCTAssertEqual(playback.requests.count, 1)
        let request = playback.requests.first
        XCTAssertEqual(request?.startIndex, 2)
        XCTAssertEqual(request?.items.map(\.id), ["1", "2", "3"])
        XCTAssertEqual(request?.items.map(\.feedId), [42, 42, 42])
        XCTAssertEqual(request?.items.map(\.podcastTitle), ["Podcast 42", "Podcast 42", "Podcast 42"])
        XCTAssertEqual(request?.startItem?.id, "3")
        XCTAssertEqual(request?.startItem?.url, "https://media.example.com/e3.mp3")
    }

    /// M3-AC14: the row of the item the player holds is marked, only for this podcast.
    func testNowPlayingEpisodeFollowsThePlayer() async {
        let playback = RecordingPlaybackController()
        let model = makeModel(FakePodcastDetailDataSource(.success(threeEpisodes)), playback: playback)
        await model.loadIfNeeded()
        XCTAssertNil(model.nowPlayingEpisodeID)

        playback.state.current = PlaybackItem(episode: .sample(2), podcast: .sample)
        XCTAssertEqual(model.nowPlayingEpisodeID, 2)

        let other = PodcastRoute(feedId: 7, title: "Other", summary: "", imageURL: nil)
        playback.state.current = PlaybackItem(episode: .sample(2), podcast: other)
        XCTAssertNil(model.nowPlayingEpisodeID)
    }

    func testPlayDoesNothingUnlessLoaded() async {
        let playback = RecordingPlaybackController()
        let model = makeModel(FakePodcastDetailDataSource(.failure(.network)), playback: playback)

        XCTAssertFalse(model.play(index: 0))
        await model.loadIfNeeded()
        XCTAssertFalse(model.play(index: 0))

        XCTAssertTrue(playback.requests.isEmpty)
    }

    func testPlayIgnoresAnIndexOutOfRange() async {
        let playback = RecordingPlaybackController()
        let model = makeModel(FakePodcastDetailDataSource(.success(threeEpisodes)), playback: playback)
        await model.loadIfNeeded()

        XCTAssertFalse(model.play(index: 3))
        XCTAssertFalse(model.play(index: -1))

        XCTAssertTrue(playback.requests.isEmpty)
    }

    // MARK: - M4 (§3.2, §3.3)

    func testSavedEpisodesShowTheOfflineBanner() async {
        let content = PodcastDetailContent(episodes: [.sample(1)], isFromCache: true)
        let model = makeModel(FakePodcastDetailDataSource(.success(content)))

        await model.loadIfNeeded()

        XCTAssertTrue(model.isShowingSavedEpisodes)
        XCTAssertEqual(model.episodes.map(\.id), [1])
    }

    func testOnlineListHasNoBannerAndRetryClearsIt() async {
        let saved = PodcastDetailContent(episodes: [.sample(1)], isFromCache: true)
        let model = makeModel(FakePodcastDetailDataSource(.success(saved), .success(threeEpisodes)))
        await model.loadIfNeeded()
        XCTAssertTrue(model.isShowingSavedEpisodes)

        await model.retry()

        XCTAssertFalse(model.isShowingSavedEpisodes)
        XCTAssertEqual(model.episodes.count, 3)
    }

    /// A tap hands the E10 models and the podcast to save (from E11, else the route) to the player.
    func testPlayCarriesTheRoomHistory() async throws {
        let episodes = [SharedModelFactory.episode(1), SharedModelFactory.episode(2)]
        let detail = PodcastDetail(episodes: episodes, feed: nil, hiddenExplicitCount: 0, isExplicitFeedHidden: false, isFromCache: false, podcast: nil)
        let content = PodcastDetailContent(episodes: episodes.map(EpisodeRow.init), source: KotlinValue(detail))
        let playback = RecordingPlaybackController()
        let model = makeModel(FakePodcastDetailDataSource(.success(content)), playback: playback)
        await model.loadIfNeeded()

        model.play(index: 1)

        let request = try XCTUnwrap(playback.requests.first)
        guard case .playlist(let podcast, let saved)? = request.history else {
            return XCTFail("expected the playlist history, got \(String(describing: request.history))")
        }
        XCTAssertEqual(saved.map(\.id), [1, 2])
        XCTAssertEqual(podcast.id, 42, "the route's feed id")
        XCTAssertEqual(podcast.title, "Podcast 42", "the route's title when E11 gave nothing")
    }
}
