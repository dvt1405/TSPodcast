import XCTest
@testable import TSPodcast

/// M2-AC10: debounce 200 ms, submit, mode switch, blank query, no duplicate request, stale
/// results dropped, empty/failed/retry (`SearchScreen` `debounce(200)`,
/// `SearchViewModel.setSearchMode`/`performSearch`, with the features-spec §3.9 [Sửa]).
@MainActor
final class SearchModelTests: XCTestCase {
    private var clock: TestClock!
    private var source: FakeSearchDataSource!
    private var model: SearchModel!

    override func setUp() async throws {
        clock = TestClock()
        source = FakeSearchDataSource()
        model = SearchModel(dataSource: source, clock: clock, debounce: .milliseconds(200))
    }

    override func tearDown() async throws {
        model = nil
        source = nil
        clock = nil
    }

    // MARK: - Helpers

    private typealias Request = FakeSearchDataSource.Request

    /// Waits (real time, at most 2 s) until `condition` holds.
    private func waitUntil(_ message: String = "", _ condition: () -> Bool, file: StaticString = #filePath, line: UInt = #line) async {
        for _ in 0..<400 where !condition() {
            try? await Task.sleep(for: .milliseconds(5))
        }
        XCTAssertTrue(condition(), message, file: file, line: line)
    }

    /// Gives started work time to reach the data source, to check that nothing was sent.
    private func settle() async {
        await TestClock.settle()
        try? await Task.sleep(for: .milliseconds(50))
        await TestClock.settle()
    }

    private func waitForRequests(_ count: Int, file: StaticString = #filePath, line: UInt = #line) async {
        await waitUntil("expected \(count) requests, got \(source.requests)", { self.source.requests.count >= count }, file: file, line: line)
    }

    private func waitForResult(file: StaticString = #filePath, line: UInt = #line) async {
        await waitUntil("still \(model.phase)", { self.model.phase != .loading }, file: file, line: line)
    }

    private func titles() -> [String] {
        if case .results(let items) = model.phase { return items.map(\.title) }
        return []
    }

    // MARK: - Debounce

    func testTypingFastSendsOneRequestAfter200ms() async {
        for text in ["v", "vi", "vie"] {
            model.queryChanged(text)
            await clock.advance(by: .milliseconds(50))
        }
        model.queryChanged("viet")

        await clock.advance(by: .milliseconds(199))
        await settle()
        XCTAssertEqual(source.requests, [], "nothing before 200 ms after the last change")

        await clock.advance(by: .milliseconds(1))
        await waitForRequests(1)
        await waitForResult()
        await settle()
        XCTAssertEqual(source.requests, [Request(query: "viet", scope: .all)])
        XCTAssertEqual(titles(), ["all:viet"])
        XCTAssertEqual(model.shownQuery, "viet")
        XCTAssertEqual(model.shownMode, .all)
    }

    func testTwoPausesSendTwoRequestsInOrder() async {
        model.queryChanged("vi")
        await clock.advance(by: .milliseconds(250))
        await waitForRequests(1)

        model.queryChanged("viet")
        await clock.advance(by: .milliseconds(200))
        await waitForRequests(2)
        await waitForResult()

        XCTAssertEqual(source.requests, [Request(query: "vi", scope: .all), Request(query: "viet", scope: .all)])
        XCTAssertEqual(titles(), ["all:viet"])
    }

    func testSubmitSearchesWithoutWaiting() async {
        model.queryChanged("vietnam")
        model.submit()

        await waitForRequests(1)
        XCTAssertEqual(source.requests, [Request(query: "vietnam", scope: .all)], "sent before the clock moved")

        await clock.advance(by: .milliseconds(200))
        await settle()
        XCTAssertEqual(source.requests.count, 1, "the pending debounce was cancelled")
        XCTAssertEqual(titles(), ["all:vietnam"])
    }

    func testQueryIsTrimmed() async {
        model.queryChanged("  vietnam  ")
        model.submit()

        await waitForRequests(1)
        XCTAssertEqual(source.requests, [Request(query: "vietnam", scope: .all)])
    }

    // MARK: - Blank query

    func testBlankQueriesSendNothing() async {
        model.queryChanged("")
        model.submit()
        model.queryChanged("   ")
        await clock.advance(by: .milliseconds(300))
        model.submit()
        await settle()

        XCTAssertEqual(source.requests, [])
        XCTAssertEqual(model.phase, .idle)
    }

    func testClearingTheQueryGoesBackToIdle() async {
        model.queryChanged("vietnam")
        model.submit()
        await waitForResult()
        XCTAssertEqual(titles(), ["all:vietnam"])

        model.queryChanged("")

        XCTAssertEqual(model.phase, .idle, "idle at once, without waiting for the debounce")
        XCTAssertEqual(model.query, "")
        await clock.advance(by: .milliseconds(300))
        await settle()
        XCTAssertEqual(source.requests.count, 1)
        XCTAssertEqual(model.phase, .idle)
    }

    func testClearingWhileARequestRunsDropsItsResult() async {
        source.hold("vietnam")
        model.queryChanged("vietnam")
        model.submit()
        await waitUntil { self.source.heldCount("vietnam") == 1 }
        XCTAssertEqual(model.phase, .loading)

        model.queryChanged("")
        source.release("vietnam")
        await settle()

        XCTAssertEqual(model.phase, .idle)
    }

    // MARK: - Duplicates

    func testSameTrimmedQueryIsNotSentAgain() async {
        model.queryChanged("vietnam")
        model.submit()
        await waitForResult()

        model.queryChanged("vietnam ")
        await clock.advance(by: .milliseconds(200))
        model.submit()
        await settle()

        XCTAssertEqual(source.requests.count, 1)
        XCTAssertEqual(titles(), ["all:vietnam"])
    }

    func testSameQueryIsNotSentAgainWhileRunning() async {
        source.hold("vietnam")
        model.queryChanged("vietnam")
        model.submit()
        await waitUntil { self.source.heldCount("vietnam") == 1 }

        model.submit()
        await settle()
        XCTAssertEqual(source.requests.count, 1)

        source.release("vietnam")
        await waitForResult()
        XCTAssertEqual(titles(), ["all:vietnam"])
    }

    // MARK: - Mode

    func testModeSwitchSearchesAgainAtOnce() async {
        model.queryChanged("piano")
        model.submit()
        await waitForResult()

        model.select(mode: .music)

        XCTAssertEqual(model.mode, .music)
        await waitForRequests(2)
        await waitForResult()
        XCTAssertEqual(source.requests, [Request(query: "piano", scope: .all), Request(query: "piano", scope: .music)])
        XCTAssertEqual(titles(), ["music:piano"])
        XCTAssertEqual(model.shownMode, .music)
    }

    func testSelectingTheSameModeSendsNothing() async {
        model.queryChanged("piano")
        model.submit()
        await waitForResult()

        model.select(mode: .all)
        await settle()

        XCTAssertEqual(source.requests.count, 1)
    }

    func testModeSwitchWithoutQueryOnlyChangesTheChip() async {
        model.select(mode: .music)
        await clock.advance(by: .milliseconds(300))
        await settle()

        XCTAssertEqual(model.mode, .music)
        XCTAssertEqual(source.requests, [])
        XCTAssertEqual(model.phase, .idle)

        model.queryChanged("piano")
        await clock.advance(by: .milliseconds(200))
        await waitForRequests(1)
        XCTAssertEqual(source.requests, [Request(query: "piano", scope: .music)], "typing uses the chosen mode")
    }

    func testModeSwitchCancelsThePendingDebounce() async {
        model.queryChanged("piano")
        await clock.advance(by: .milliseconds(100))
        model.select(mode: .music)
        await waitForRequests(1)

        await clock.advance(by: .milliseconds(200))
        await settle()

        XCTAssertEqual(source.requests, [Request(query: "piano", scope: .music)])
    }

    // MARK: - Stale results

    func testOlderResultNeverOverwritesANewerOne() async {
        source.hold("a")
        model.queryChanged("a")
        model.submit()
        await waitUntil { self.source.heldCount("a") == 1 }

        model.queryChanged("b")
        model.submit()
        await waitForRequests(2)
        await waitUntil { self.titles() == ["all:b"] }

        source.release("a")
        await settle()

        XCTAssertEqual(titles(), ["all:b"], "A came back last but B is the newer request")
        XCTAssertEqual(model.shownQuery, "b")
    }

    // MARK: - Loading, empty, failure

    func testLoadingWhileTheRequestRuns() async {
        source.hold("vietnam")
        model.queryChanged("vietnam")
        model.submit()
        await waitUntil { self.source.heldCount("vietnam") == 1 }

        XCTAssertEqual(model.phase, .loading)

        source.release("vietnam")
        await waitForResult()
        XCTAssertEqual(titles(), ["all:vietnam"])
    }

    func testNoResultsIsEmptyWithItsQueryAndMode() async {
        source.setResult(.success([]), for: "zzqxjvkwpt")
        model.select(mode: .music)
        model.queryChanged("zzqxjvkwpt")
        model.submit()
        await waitForResult()

        XCTAssertEqual(model.phase, .empty)
        XCTAssertEqual(model.shownQuery, "zzqxjvkwpt")
        XCTAssertEqual(model.shownMode, .music)
    }

    func testFailureThenRetrySendsTheSamePair() async {
        source.setResult(.failure(.network), for: "vietnam")
        model.select(mode: .music)
        model.queryChanged("vietnam")
        model.submit()
        await waitForResult()
        XCTAssertEqual(model.phase, .failed)

        source.setResult(.success([.sample(query: "vietnam", scope: .music)]), for: "vietnam")
        model.retry()
        XCTAssertEqual(model.phase, .loading)
        await waitForRequests(2)
        await waitForResult()

        XCTAssertEqual(source.requests, [Request(query: "vietnam", scope: .music), Request(query: "vietnam", scope: .music)])
        XCTAssertEqual(titles(), ["music:vietnam"])
    }

    func testAFailedPairCanBeSubmittedAgain() async {
        source.setResult(.failure(.network), for: "vietnam")
        model.queryChanged("vietnam")
        model.submit()
        await waitForResult()
        XCTAssertEqual(model.phase, .failed)

        model.submit()
        await waitForRequests(2)
        await waitForResult()
        XCTAssertEqual(model.phase, .failed)
    }

    func testRetryOutsideTheFailedStateSendsNothing() async {
        model.retry()
        model.queryChanged("vietnam")
        model.submit()
        await waitForResult()
        model.retry()
        await settle()

        XCTAssertEqual(source.requests.count, 1)
    }
}
