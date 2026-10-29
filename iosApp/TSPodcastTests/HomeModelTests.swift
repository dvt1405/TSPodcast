import XCTest
@testable import TSPodcast

/// M1-AC17, M1-AC18: per-section state, isolated failures, per-section retry and
/// pull-to-refresh of Home (`MainViewModel` with the features-spec §3.4 [Sửa]).
@MainActor
final class HomeModelTests: XCTestCase {
    func testEverySectionStartsLoading() {
        let model = HomeModel(dataSource: FakeHomeDataSource())

        XCTAssertEqual(model.trending, .loading)
        XCTAssertEqual(model.music, .loading)
        XCTAssertEqual(model.live, .loading)
        XCTAssertEqual(model.recent, .loading)
        XCTAssertEqual(model.forYou, .loading)
        XCTAssertEqual(model.generation, 0)
    }

    func testLoadFillsEverySectionOnce() async {
        let source = FakeHomeDataSource()
        let model = HomeModel(dataSource: source)

        await model.loadIfNeeded()
        await model.loadIfNeeded()

        XCTAssertEqual(model.trending, .loaded([.sample(1)]))
        XCTAssertEqual(model.forYou, .loaded([.sample(2)]))
        XCTAssertEqual(model.music, .loaded([.sample(3)]))
        XCTAssertEqual(model.live, .loaded([[.sample(4)]]))
        XCTAssertEqual(model.recent, .loaded([.sample(5)]))
        XCTAssertEqual(model.generation, 1)
        XCTAssertEqual(source.allCalls, [.trendingSections: 1, .music: 1, .live: 1, .recent: 1], "a second loadIfNeeded does nothing")
    }

    func testEmptyListGivesEmpty() async {
        let source = FakeHomeDataSource(recent: .success([]))
        let model = HomeModel(dataSource: source)

        await model.loadIfNeeded()

        XCTAssertEqual(model.recent, .empty)
    }

    func testErrorGivesFailed() async {
        let source = FakeHomeDataSource(recent: .failure(.network))
        let model = HomeModel(dataSource: source)

        await model.loadIfNeeded()

        XCTAssertEqual(model.recent, .failed)
    }

    func testMusicFailureIsIsolated() async {
        let source = FakeHomeDataSource(music: .failure(.network))
        let model = HomeModel(dataSource: source)

        await model.loadIfNeeded()

        XCTAssertEqual(model.music, .failed)
        XCTAssertEqual(model.trending, .loaded([.sample(1)]))
        XCTAssertEqual(model.forYou, .loaded([.sample(2)]))
        XCTAssertEqual(model.live, .loaded([[.sample(4)]]))
        XCTAssertEqual(model.recent, .loaded([.sample(5)]))
    }

    func testBothTrendingCallsFailingMarksBothFailed() async {
        let source = FakeHomeDataSource(trending: .failure(.network), forYou: .failure(.network))
        let model = HomeModel(dataSource: source)

        await model.loadIfNeeded()

        XCTAssertEqual(model.trending, .failed)
        XCTAssertEqual(model.forYou, .failed)
        XCTAssertEqual(model.music, .loaded([.sample(3)]))
    }

    func testRetryLiveCallsOnlyTheLiveLoader() async {
        let source = FakeHomeDataSource(live: .failure(.network))
        let model = HomeModel(dataSource: source)
        await model.loadIfNeeded()
        XCTAssertEqual(model.live, .failed)

        source.setLive(.success((1...4).map(LiveRowItem.sample)))
        source.hold(.live)
        let retry = Task { await model.retry(.live) }
        await waitUntil { source.heldCount == 1 }

        XCTAssertEqual(model.live, .loading, "the section shimmers while it retries")
        source.releaseAll()
        await retry.value

        XCTAssertEqual(model.live, .loaded([[.sample(1), .sample(2), .sample(3)], [.sample(4)]]))
        XCTAssertEqual(source.allCalls, [.trendingSections: 1, .music: 1, .live: 2, .recent: 1])
        XCTAssertEqual(model.generation, 1, "a retry is not a full reload")
    }

    func testRetryForYouReloadsTheSharedTrendingCall() async {
        let source = FakeHomeDataSource(trending: .failure(.network), forYou: .failure(.network))
        let model = HomeModel(dataSource: source)
        await model.loadIfNeeded()

        source.setTrending(.success([.sample(7)]), forYou: .success([.sample(7)]))
        await model.retry(.forYou)

        XCTAssertEqual(model.trending, .loaded([.sample(7)]))
        XCTAssertEqual(model.forYou, .loaded([.sample(7)]))
        XCTAssertEqual(source.allCalls, [.trendingSections: 2, .music: 1, .live: 1, .recent: 1])
    }

    func testRefreshReloadsAllSections() async {
        let source = FakeHomeDataSource(recent: .failure(.network))
        let model = HomeModel(dataSource: source)
        await model.loadIfNeeded()
        XCTAssertEqual(model.generation, 1)

        source.setTrending(.success([.sample(11)]), forYou: .success([.sample(12)]))
        source.setMusic(.success([.sample(13)]))
        source.setLive(.success([.sample(14)]))
        source.setRecent(.success([.sample(15)]))
        source.hold(.trendingSections, .music, .live, .recent)
        let refresh = Task { await model.refresh() }
        await waitUntil { source.heldCount == 4 }

        // While waiting: loaded content stays, the section without content shimmers.
        XCTAssertEqual(model.trending, .loaded([.sample(1)]))
        XCTAssertEqual(model.forYou, .loaded([.sample(2)]))
        XCTAssertEqual(model.music, .loaded([.sample(3)]))
        XCTAssertEqual(model.live, .loaded([[.sample(4)]]))
        XCTAssertEqual(model.recent, .loading)
        XCTAssertEqual(model.generation, 1)

        source.releaseAll()
        await refresh.value

        XCTAssertEqual(source.allCalls, [.trendingSections: 2, .music: 2, .live: 2, .recent: 2], "each loader once more")
        XCTAssertEqual(model.trending, .loaded([.sample(11)]))
        XCTAssertEqual(model.forYou, .loaded([.sample(12)]))
        XCTAssertEqual(model.music, .loaded([.sample(13)]))
        XCTAssertEqual(model.live, .loaded([[.sample(14)]]))
        XCTAssertEqual(model.recent, .loaded([.sample(15)]))
        XCTAssertEqual(model.generation, 2)
    }

    func testFailedRefreshKeepsShownContent() async {
        let source = FakeHomeDataSource()
        let model = HomeModel(dataSource: source)
        await model.loadIfNeeded()

        source.setMusic(.failure(.network))
        source.setRecent(.success([]))
        await model.refresh()

        XCTAssertEqual(model.music, .loaded([.sample(3)]), "stale content beats an error after a refresh")
        XCTAssertEqual(model.recent, .empty, "a successful empty answer replaces the old list")
        XCTAssertEqual(model.generation, 2)
    }

    func testLateResultOfAnOlderRequestIsDropped() async {
        let source = FakeHomeDataSource(music: .failure(.network))
        let model = HomeModel(dataSource: source)
        source.hold(.music)
        let firstLoad = Task { await model.loadIfNeeded() }
        await waitUntil { source.heldCount == 1 }

        // A retry starts a newer music request; the held, older one must not overwrite it.
        source.releaseAll()
        source.setMusic(.success([.sample(30)]))
        await model.retry(.music)
        await firstLoad.value

        XCTAssertEqual(model.music, .loaded([.sample(30)]))
    }

    func testSectionLoaderMapping() {
        XCTAssertEqual(HomeModel.loader(for: .trending), .trendingSections)
        XCTAssertEqual(HomeModel.loader(for: .forYou), .trendingSections)
        XCTAssertEqual(HomeModel.loader(for: .music), .music)
        XCTAssertEqual(HomeModel.loader(for: .live), .live)
        XCTAssertEqual(HomeModel.loader(for: .recent), .recent)
        XCTAssertEqual(HomeSection.allCases, [.trending, .music, .live, .recent, .forYou], "Android order")
    }

    /// Yields to other tasks until `condition` holds (fails after 2 s).
    private func waitUntil(_ condition: () -> Bool, file: StaticString = #filePath, line: UInt = #line) async {
        let deadline = Date().addingTimeInterval(2)
        while !condition() {
            if Date() > deadline {
                XCTFail("condition not met in 2 s", file: file, line: line)
                return
            }
            try? await Task.sleep(for: .milliseconds(5))
        }
    }
}
