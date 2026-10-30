import XCTest
@testable import TSPodcast

/// `HiddenShowsStore` in memory.
final class FakeHiddenShowsStore: HiddenShowsStore, @unchecked Sendable {
    private let lock = NSLock()
    private var shows: [HiddenShowItem] = []
    private var clock = 0.0

    func all() async -> [HiddenShowItem] { lock.withLock { shows.sorted { $0.hiddenAt > $1.hiddenAt } } }

    func hide(feedId: Int64, title: String, imageURL: String?) async {
        lock.withLock {
            clock += 1
            shows.removeAll { $0.feedId == feedId }
            shows.append(HiddenShowItem(feedId: feedId, title: title, imageURL: imageURL, hiddenAt: Date(timeIntervalSince1970: clock)))
        }
    }

    func unhide(feedId: Int64) async {
        lock.withLock { shows.removeAll { $0.feedId == feedId } }
    }
}

/// M6-AC11: hiding a show removes it from the screens at once, logs `show_hidden` and is listed
/// in Settings → "Show đã ẩn" until unhidden.
@MainActor
final class HiddenShowsModelTests: XCTestCase {
    func testHideNotifiesSavesAndLogsTheFeedIdOnly() async {
        let analytics = RecordingAnalyticsTracker()
        let model = HiddenShowsModel(store: FakeHiddenShowsStore(), analytics: analytics)
        var removed: [Int64] = []
        model.onHide { removed.append($0) }

        await model.hide(feedId: 42, title: "Spam show", imageURL: nil, screen: "PodcastDetail")

        XCTAssertEqual(removed, [42])
        XCTAssertTrue(model.isHidden(42))
        XCTAssertEqual(model.items.map(\.title), ["Spam show"])
        XCTAssertEqual(analytics.events.map(\.name), ["show_hidden"])
        XCTAssertEqual(analytics.events.first?.params, ["feed_id": "42"], "no title in the event")
    }

    func testListIsNewestFirstAndUnhideRemoves() async {
        let store = FakeHiddenShowsStore()
        let model = HiddenShowsModel(store: store, analytics: RecordingAnalyticsTracker())
        await model.hide(feedId: 1, title: "One", imageURL: nil, screen: "Discover")
        await model.hide(feedId: 2, title: "Two", imageURL: nil, screen: "Search")
        XCTAssertEqual(model.items.map(\.feedId), [2, 1])

        await model.unhide(feedId: 2)

        XCTAssertEqual(model.items.map(\.feedId), [1])
        XCTAssertFalse(model.isHidden(2))

        let relaunched = HiddenShowsModel(store: store, analytics: RecordingAnalyticsTracker())
        await relaunched.load()
        XCTAssertEqual(relaunched.items.map(\.feedId), [1], "the store keeps the list")
    }

    func testHomeDropsAHiddenShowFromEverySection() async {
        let source = FakeHomeDataSource(
            trending: .success([.sample(1), .sample(2)]),
            forYou: .success([.sample(2)]),
            music: .success([.sample(3)]),
            live: .success([.sample(4), .sample(5)]),
            recent: .success([.sample(2), .sample(6)])
        )
        let model = HomeModel(dataSource: source)
        await model.loadIfNeeded()

        model.removeShow(feedId: 2)
        model.removeShow(feedId: LiveRowItem.sample(4).feedId)

        XCTAssertEqual(model.trending, .loaded([.sample(1)]))
        XCTAssertEqual(model.forYou, .empty, "a section left with nothing is empty")
        XCTAssertEqual(model.music, .loaded([.sample(3)]))
        XCTAssertEqual(model.recent, .loaded([.sample(6)]))
        XCTAssertEqual(model.live, .loaded([[.sample(5)]]))
    }

    func testSearchDropsAHiddenShowFromTheResults() async {
        let clock = TestClock()
        let source = FakeSearchDataSource()
        source.setResult(.success([
            .sample(query: "tin", scope: .all, id: 7),
            .sample(query: "tin", scope: .all, id: 8),
        ]), for: "tin")
        let model = SearchModel(dataSource: source, clock: clock, debounce: .milliseconds(200))
        model.queryChanged("tin")
        await clock.advance(by: .milliseconds(200))
        for _ in 0..<400 where !(model.phase != .loading && model.phase != .idle) {
            try? await Task.sleep(for: .milliseconds(5))
        }

        model.removeShow(feedId: 7)
        XCTAssertEqual(model.phase, .results([.sample(query: "tin", scope: .all, id: 8)]))

        model.removeShow(feedId: 8)
        XCTAssertEqual(model.phase, .empty)
    }
}
