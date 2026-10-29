import XCTest
@testable import TSPodcast

/// M2-AC4: states, alert, inline retry, single load and the playlist handed to the player seam
/// (`PodcastViewModel` with the features-spec §3.5 [Sửa]).
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

        model.play(index: 2)

        XCTAssertEqual(playback.requests.count, 1)
        let request = playback.requests.first
        XCTAssertEqual(request?.startIndex, 2)
        XCTAssertEqual(request?.episodes, [.sample(1), .sample(2), .sample(3)])
        XCTAssertEqual(request?.podcast.feedId, 42)
        XCTAssertEqual(request?.startEpisode, .sample(3))
    }

    func testPlayDoesNothingUnlessLoaded() async {
        let playback = RecordingPlaybackController()
        let model = makeModel(FakePodcastDetailDataSource(.failure(.network)), playback: playback)

        model.play(index: 0)
        await model.loadIfNeeded()
        model.play(index: 0)

        XCTAssertTrue(playback.requests.isEmpty)
    }

    func testPlayIgnoresAnIndexOutOfRange() async {
        let playback = RecordingPlaybackController()
        let model = makeModel(FakePodcastDetailDataSource(.success(threeEpisodes)), playback: playback)
        await model.loadIfNeeded()

        model.play(index: 3)
        model.play(index: -1)

        XCTAssertTrue(playback.requests.isEmpty)
    }
}
