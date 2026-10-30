import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M4-AC14: the Favourite tab follows the table, a tap plays and pushes Detail without opening
/// the Player, and a row that cannot be opened offers "Bỏ yêu thích" (features-spec §3.8,
/// Android `FavouriteScreen`/`FavouriteViewModel`).
@MainActor
final class FavouriteModelTests: XCTestCase {
    private var source: FakeFavouriteDataSource!
    private var playback: RecordingPlaybackController!

    override func setUp() async throws {
        source = FakeFavouriteDataSource()
        playback = RecordingPlaybackController()
    }

    private func makeModel() -> FavouriteModel {
        FavouriteModel(dataSource: source, playback: playback)
    }

    func testLoadingUntilTheFirstListThenEmpty() {
        let model = makeModel()
        XCTAssertEqual(model.state, .loading)
        XCTAssertEqual(source.observeCount, 1)

        source.emit([])

        XCTAssertEqual(model.state, .empty)
        XCTAssertTrue(model.rows.isEmpty)
    }

    /// [Sửa] Android read the list once; here a favourite added elsewhere shows without a reload.
    func testNewFavouritesShowWithoutReloading() {
        let model = makeModel()
        source.emit([])

        source.emit([.episode(1)])
        XCTAssertEqual(model.rows, [.episode(1)])

        source.emit([.episode(2), .episode(1)])
        XCTAssertEqual(model.rows.map(\.itemID), ["2", "1"], "newest first, as the repository sends it")
        XCTAssertEqual(source.observeCount, 1, "one observation, never a second load")

        source.emit([])
        XCTAssertEqual(model.state, .empty)
    }

    func testSelectPlaysAndPushesDetailWithoutTheRequestOpeningThePlayer() async {
        let model = makeModel()
        source.emit([.podcast(7), .episode(2)])
        let route = PodcastRoute(feedId: 42, title: "Podcast 42", summary: "About 42", imageURL: nil)
        let request = PlaybackRequest(podcast: route, episodes: [3, 2, 1].map(EpisodeRow.sample), startIndex: 1)
        source.resolutions[FavouriteRow.episode(2).id] = FavouritePlayback(request: request, route: route)

        await model.select(index: 1)

        XCTAssertEqual(playback.requests, [request], "exactly one request, the resolved one")
        XCTAssertEqual(playback.requests.first?.startItem?.id, "2")
        XCTAssertEqual(model.path, [route], "Detail of the feed is pushed on the Favourite tab")
        XCTAssertNil(model.unavailable)
    }

    func testRowThatCannotBeOpenedShowsTheAlertAndCanBeRemoved() async {
        let model = makeModel()
        source.emit([.episode(5), .episode(4)])

        await model.select(index: 0)

        XCTAssertEqual(model.unavailable, .episode(5))
        XCTAssertTrue(playback.requests.isEmpty)
        XCTAssertTrue(model.path.isEmpty)

        await model.removeUnavailable()

        XCTAssertEqual(source.removed, [.episode(5)], "Bỏ yêu thích deletes the row")
        XCTAssertNil(model.unavailable)
        XCTAssertEqual(model.rows, [.episode(4)], "the list follows the table")
    }

    func testCloseKeepsTheRow() async {
        let model = makeModel()
        source.emit([.episode(5)])
        await model.select(index: 0)

        model.dismissUnavailable()

        XCTAssertNil(model.unavailable)
        XCTAssertTrue(source.removed.isEmpty)
        XCTAssertEqual(model.rows, [.episode(5)])
    }

    func testOutOfRangeTapDoesNothing() async {
        let model = makeModel()
        source.emit([.episode(1)])

        await model.select(index: 3)

        XCTAssertTrue(source.resolved.isEmpty)
        XCTAssertTrue(playback.requests.isEmpty)
    }

    func testReleasingTheModelStopsTheObservation() {
        var model: FavouriteModel? = makeModel()
        XCTAssertEqual(source.observerCount, 1)
        XCTAssertNotNil(model)

        model = nil

        XCTAssertEqual(source.observerCount, 0)
    }

    // MARK: - Over the real repository (Room file, shared `watch`)

    func testSharedDataSourceFollowsTheTableAndResolvesSavedRows() async throws {
        let fixture = SharedGraphFixture()
        defer { fixture.close() }
        let model = FavouriteModel(
            dataSource: SharedFavouriteDataSource(repository: fixture.graph.favouriteRepository, resolver: fixture.graph.favouriteResolver),
            playback: playback
        )
        await waitUntil("first list") { model.state == .empty }

        let podcast = SharedModelFactory.podcast(42)
        let episodes = (1...3).map { SharedModelFactory.episode(Int64($0)) }
        try await fixture.graph.podcastStorage.insertPodcast(podcast: podcast)
        try await fixture.graph.podcastStorage.insertEpisodes(episodes: episodes)
        let favourite = FavouriteItem(id: "2", type: .podcastepisode, title: "Episode 2", image: nil, description: "<b>Summary</b> 2")
        try await fixture.graph.favouriteRepository.setFavourite(item: favourite, isFavourite: true)

        await waitUntil("row from the table") { model.rows.count == 1 }
        let row = try XCTUnwrap(model.rows.first)
        XCTAssertEqual(row.kind, .episode)
        XCTAssertEqual(row.summary, "Summary 2", "HTML description shown as text")

        await model.select(index: 0)

        let request = try XCTUnwrap(playback.requests.first)
        XCTAssertEqual(request.items.map(\.id), ["3", "2", "1"], "saved episodes, newest first")
        XCTAssertEqual(request.startIndex, 1)
        XCTAssertNotNil(request.history, "the playlist is saved again like playerEpisode")
        XCTAssertEqual(model.path.map(\.feedId), [42])

        // A favourite whose episode is not saved cannot be opened; "Bỏ yêu thích" deletes it.
        let orphan = FavouriteItem(id: "99", type: .podcastepisode, title: "Gone", image: nil, description: nil)
        try await fixture.graph.favouriteRepository.setFavourite(item: orphan, isFavourite: true)
        await waitUntil("orphan row") { model.rows.count == 2 }
        XCTAssertEqual(model.rows.first?.itemID, "99")

        await model.select(index: 0)
        XCTAssertEqual(model.unavailable?.itemID, "99")
        await model.removeUnavailable()

        await waitUntil("orphan removed") { model.rows.map(\.itemID) == ["2"] }
        let stillThere = try await fixture.graph.favouriteRepository.isFavourite(type: .podcastepisode, id: "99").boolValue
        XCTAssertFalse(stillThere)
    }
}
