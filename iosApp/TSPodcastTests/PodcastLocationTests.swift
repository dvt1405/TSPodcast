import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// `PodcastLocationStore` in memory, normalising like shared (known, deduplicated, at most 3).
final class FakePodcastLocationStore: PodcastLocationStore, @unchecked Sendable {
    private let lock = NSLock()
    private var regions: [String]
    private(set) var saves: [[String]] = []
    private(set) var loads = 0

    init(_ regions: [String] = ["VN"]) {
        self.regions = regions
    }

    func load() async -> [PodcastLocationItem] {
        lock.withLock {
            loads += 1
            return regions.compactMap(PodcastLocationItem.item)
        }
    }

    func save(regions newRegions: [String]) async -> [PodcastLocationItem] {
        lock.withLock {
            saves.append(newRegions)
            var seen = Set<String>()
            let kept = newRegions.filter { PodcastLocationItem.item($0) != nil && seen.insert($0).inserted }.prefix(3)
            if !kept.isEmpty { regions = Array(kept) }
            return regions.compactMap(PodcastLocationItem.item)
        }
    }
}

extension PodcastLocationItem {
    static func item(_ region: String) -> PodcastLocationItem? {
        supported.first { $0.region == region }
    }

    static let vietnam = item("VN")!
    static let usa = item("US")!
    static let japan = item("JP")!
    static let brazil = item("BR")!
}

/// "Vị trí podcast": the shared list and rules seen from Swift, and the editor's model.
@MainActor
final class PodcastLocationsModelTests: XCTestCase {
    func testSupportedLocationsComeFromShared() {
        let supported = PodcastLocationItem.supported
        XCTAssertEqual(supported.count, 18)
        XCTAssertEqual(supported.first, PodcastLocationItem(region: "VN", contentCode: "vi", flag: "🇻🇳"))
        XCTAssertEqual(Set(supported.map(\.contentCode)).count, 18, "one location per language")
        XCTAssertEqual(PodcastLocationItem.maxLocations, 3)
        XCTAssertFalse(supported.contains { $0.region == "XX" })
    }

    func testNamesFollowTheLocaleAndLanguagesTheirOwnName() {
        XCTAssertEqual(PodcastLocationItem.vietnam.name(in: Locale(identifier: "vi")), "Việt Nam")
        XCTAssertEqual(PodcastLocationItem.vietnam.name(in: Locale(identifier: "en")), "Vietnam")
        XCTAssertEqual(PodcastLocationItem.vietnam.languageName, "Tiếng Việt")
        XCTAssertEqual(PodcastLocationItem.usa.languageName, "English")
        XCTAssertEqual(PodcastLocationItem.brazil.languageName, "Português")
        XCTAssertEqual(PodcastLocationItem.japan.flagAndName(in: Locale(identifier: "en")), "🇯🇵 Japan")
    }

    func testLoadReadsTheStoreOnce() async {
        let store = FakePodcastLocationStore(["JP", "VN"])
        let model = PodcastLocationsModel(store: store, analytics: RecordingAnalyticsTracker())
        XCTAssertEqual(model.locations, [])

        await model.load()
        await model.load()

        XCTAssertEqual(model.locations, [.japan, .vietnam])
        XCTAssertEqual(model.primary, .japan)
        XCTAssertEqual(store.loads, 1)
        XCTAssertEqual(model.others.count, 16)
        XCTAssertFalse(model.others.contains(.japan))
    }

    func testAddUpToThreeThenFull() async {
        let model = PodcastLocationsModel(store: FakePodcastLocationStore(["VN"]), analytics: RecordingAnalyticsTracker())
        await model.load()
        XCTAssertFalse(model.canRemove, "the last location stays")

        await model.add(.usa)
        await model.add(.usa)
        await model.add(.japan)
        XCTAssertTrue(model.isFull)
        await model.add(.brazil)

        XCTAssertEqual(model.locations, [.vietnam, .usa, .japan])
        XCTAssertTrue(model.canRemove)
    }

    func testRemoveButNeverTheLast() async {
        let model = PodcastLocationsModel(store: FakePodcastLocationStore(["VN", "US"]), analytics: RecordingAnalyticsTracker())
        await model.load()

        await model.remove(.vietnam)
        XCTAssertEqual(model.locations, [.usa], "the next one becomes primary")
        await model.remove(.usa)
        XCTAssertEqual(model.locations, [.usa])
    }

    func testMoveUpDownAndDrag() async {
        let model = PodcastLocationsModel(store: FakePodcastLocationStore(["VN", "US", "JP"]), analytics: RecordingAnalyticsTracker())
        await model.load()

        await model.move(at: 2, by: -1)
        XCTAssertEqual(model.locations, [.vietnam, .japan, .usa])
        await model.move(at: 0, by: -1)
        await model.move(at: 2, by: 1)
        XCTAssertEqual(model.locations, [.vietnam, .japan, .usa], "out of range does nothing")
        await model.move(fromOffsets: IndexSet(integer: 2), toOffset: 0)
        XCTAssertEqual(model.locations, [.usa, .vietnam, .japan])
    }

    func testEveryChangeIsLoggedAndToldOnceLikeAndroid() async {
        let analytics = RecordingAnalyticsTracker()
        let model = PodcastLocationsModel(store: FakePodcastLocationStore(["VN"]), analytics: analytics)
        var changes: [([String], [String])] = []
        model.onChange { previous, current in changes.append((previous.map(\.region), current.map(\.region))) }
        await model.load()

        await model.add(.usa)
        await model.move(at: 1, by: -1)
        await model.move(at: 0, by: -1) // no change: nothing logged

        XCTAssertEqual(changes.map(\.0), [["VN"], ["VN", "US"]])
        XCTAssertEqual(changes.map(\.1), [["VN", "US"], ["US", "VN"]])
        XCTAssertEqual(analytics.events.map(\.name), ["podcast_locations_change", "podcast_locations_change"])
        XCTAssertEqual(analytics.events[0].params, [
            "count": "2", "primary_location": "VN", "locations": "VN,US", "primary_changed": "false", "change": "add",
        ])
        XCTAssertEqual(analytics.events[1].params["primary_changed"], "true")
        XCTAssertEqual(analytics.events[1].params["change"], "reorder")
    }

    func testChangeKinds() {
        XCTAssertEqual(PodcastLocationChange.between([.vietnam], [.vietnam]), .none)
        XCTAssertEqual(PodcastLocationChange.between([.vietnam, .usa], [.usa, .vietnam]), .primary)
        XCTAssertEqual(PodcastLocationChange.between([.vietnam], [.vietnam, .japan]), .secondary)
        XCTAssertEqual(PodcastLocationChange.between([.vietnam, .usa, .japan], [.vietnam, .japan, .usa]), .secondary)
    }
}

/// "Podcast theo vị trí" and "Thời sự" per location on Home, and the reload rules of Android
/// `MainViewModel` when the locations change.
@MainActor
final class HomeLocationTests: XCTestCase {
    private func makeModel(
        _ regions: [String] = ["VN", "US"],
        source: FakeHomeDataSource = FakeHomeDataSource(),
        newsHub: FakeNewsHubDataSource? = nil
    ) -> (HomeModel, PodcastLocationsModel, FakeHomeDataSource) {
        let locations = PodcastLocationsModel(store: FakePodcastLocationStore(regions), analytics: RecordingAnalyticsTracker())
        let model = HomeModel(dataSource: source, newsHub: newsHub, locations: locations)
        locations.onChange { [weak model] previous, current in
            Task { await model?.locationsChanged(from: previous, to: current) }
        }
        return (model, locations, source)
    }

    func testFirstLoadFetchesEachLocationOnce() async {
        let source = FakeHomeDataSource()
        source.setPodcasts(.success([.sample(10), .sample(11)]), in: "VN")
        source.setPodcasts(.failure(.network), in: "US")
        source.setNews([.sample(1, feedId: 50)], in: "US")
        let (model, _, _) = makeModel(source: source)

        await model.loadIfNeeded()

        XCTAssertEqual(model.locations, [.vietnam, .usa])
        XCTAssertEqual(Set(source.locationCalls), ["podcasts.VN", "podcasts.US", "news.VN", "news.US"])
        XCTAssertEqual(model.locationPodcasts["VN"], .loaded([.sample(10), .sample(11)]))
        XCTAssertEqual(model.locationPodcasts["US"], .failed)
        XCTAssertEqual(model.locationNews["VN"], [])
        XCTAssertEqual(model.locationNews["US"], [.sample(1, feedId: 50)])
        XCTAssertEqual(source.allCalls, [.trendingSections: 1, .music: 1, .live: 1, .recent: 1], "the other loaders once")
    }

    func testRefreshForcesTheLocationLists() async {
        let (model, _, source) = makeModel(["VN"])
        await model.loadIfNeeded()

        await model.refresh()

        XCTAssertEqual(source.locationCalls.filter { $0.hasSuffix(".force") }.sorted(), ["news.VN.force", "podcasts.VN.force"])
    }

    func testRetryOneLocation() async {
        let source = FakeHomeDataSource()
        source.setPodcasts(.failure(.network), in: "US")
        let (model, _, _) = makeModel(source: source)
        await model.loadIfNeeded()

        source.setPodcasts(.success([.sample(20)]), in: "US")
        await model.retryLocation("US")

        XCTAssertEqual(model.locationPodcasts["US"], .loaded([.sample(20)]))
        XCTAssertEqual(source.locationCalls.last, "podcasts.US.force")
    }

    func testAnotherPrimaryReloadsEveryList() async {
        let (model, locations, source) = makeModel()
        await model.loadIfNeeded()

        await locations.move(at: 1, by: -1)
        await waitUntil { source.allCalls[.trendingSections] == 2 && model.generation == 2 }

        XCTAssertEqual(source.allCalls, [.trendingSections: 2, .music: 2, .live: 2, .recent: 2])
        XCTAssertEqual(model.locations, [.usa, .vietnam])
    }

    func testASecondaryChangeReloadsOnlyTheLocationLists() async {
        let (model, locations, source) = makeModel()
        await model.loadIfNeeded()
        source.setPodcasts(.success([.sample(30)]), in: "JP")

        await locations.add(.japan)
        await waitUntil { model.locationPodcasts["JP"] == .loaded([.sample(30)]) }
        await locations.remove(.usa)
        await waitUntil { model.locationPodcasts["US"] == nil }

        XCTAssertEqual(source.allCalls, [.trendingSections: 1, .music: 1, .live: 1, .recent: 1], "Home lists untouched")
        XCTAssertNil(model.locationNews["US"], "a removed location's lists are dropped")
        XCTAssertEqual(model.generation, 1)
    }

    func testThePrimaryListSkipsTheTopTrendingShows() {
        let trending = (1...10).map { PodcastCard.sample(Int64($0)) }
        let many = (1...20).map { PodcastCard.sample(Int64($0)) }
        XCTAssertEqual(HomeModel.withoutTopTrending(many, trending: trending), (11...20).map { PodcastCard.sample(Int64($0)) })
        let few = (1...14).map { PodcastCard.sample(Int64($0)) }
        XCTAssertEqual(HomeModel.withoutTopTrending(few, trending: trending), few, "14 shows: kept as they are")
    }

    func testOnlyThePrimaryLocationIsDeduplicated() async {
        let source = FakeHomeDataSource(trending: .success((1...10).map { PodcastCard.sample(Int64($0)) }))
        let list = (1...20).map { PodcastCard.sample(Int64($0)) }
        source.setPodcasts(.success(list), in: "VN")
        source.setPodcasts(.success(list), in: "US")
        let (model, _, _) = makeModel(source: source)
        await model.loadIfNeeded()

        XCTAssertEqual(model.displayedLocationPodcasts("VN"), .loaded(Array(list.dropFirst(10))))
        XCTAssertEqual(model.displayedLocationPodcasts("US"), .loaded(list))
        XCTAssertNil(model.displayedLocationPodcasts("JP"))
    }

    func testTheHubReplacesThePrimaryLocationsNews() async {
        let source = FakeHomeDataSource()
        source.setNews([.sample(1, feedId: 50)], in: "US")
        let hub = FakeNewsHubDataSource()
        let (model, _, _) = makeModel(source: source, newsHub: hub)
        await model.loadIfNeeded()
        XCTAssertEqual(model.newsRegions(), [.vietnam, .usa], "primary from the hub, US from the catalog")

        hub.setResult(.success(nil))
        await model.retry(.news)
        XCTAssertEqual(model.newsRegions(), [.usa], "no hub and no catalog news for Vietnam: no chip")
    }

    func testHidingAShowDropsItFromTheLocationLists() async {
        let source = FakeHomeDataSource()
        source.setPodcasts(.success([.sample(10), .sample(11)]), in: "VN")
        source.setNews([.sample(1, feedId: 10), .sample(2, feedId: 12)], in: "US")
        let (model, _, _) = makeModel(source: source)
        await model.loadIfNeeded()

        model.removeShow(feedId: 10)

        XCTAssertEqual(model.locationPodcasts["VN"], .loaded([.sample(11)]))
        XCTAssertEqual(model.locationNews["US"], [.sample(2, feedId: 12)])
    }

    func testWithoutLocationsNothingLoads() async {
        let source = FakeHomeDataSource()
        let model = HomeModel(dataSource: source)
        await model.loadIfNeeded()

        XCTAssertEqual(model.locations, [])
        XCTAssertEqual(source.locationCalls, [])
        XCTAssertEqual(model.newsRegions(), [])
    }

    /// Polls the main actor until `condition` holds (the change listener runs in its own Task).
    private func waitUntil(timeout: Duration = .seconds(2), _ condition: () -> Bool) async {
        let clock = ContinuousClock()
        let deadline = clock.now + timeout
        while !condition(), clock.now < deadline {
            try? await Task.sleep(for: .milliseconds(10))
        }
        XCTAssertTrue(condition(), "condition not met in time")
    }
}

/// The topic page header follows the primary location.
@MainActor
final class CategoryLocationTests: XCTestCase {
    func testTheTopicPageNamesThePrimaryLocation() async {
        let locations = PodcastLocationsModel(store: FakePodcastLocationStore(["BR", "VN"]), analytics: RecordingAnalyticsTracker())
        let model = CategoryPodcastsModel(
            category: CategoryItem(id: 55, name: "News"),
            dataSource: FakeSearchDataSource(categoryResults: .success([.sample(1)])),
            locations: locations
        )
        XCTAssertNil(model.location)

        await model.loadIfNeeded()

        XCTAssertEqual(model.location, .brazil)
        XCTAssertEqual(model.state, .loaded([.sample(1)]))
    }
}
