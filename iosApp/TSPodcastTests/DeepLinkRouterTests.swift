import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M4-AC20: Universal Links (features-spec §3.12, M4 §4.5). Podcast links open Detail on Discover,
/// episode links play through E12 and open Detail, links before Main wait for it, and nothing is
/// accepted while no host is configured.
@MainActor
final class DeepLinkRouterTests: XCTestCase {
    private var source: FakeDeepLinkDataSource!
    private var playback: RecordingPlaybackController!
    private var clock: Date!

    override func setUp() async throws {
        source = FakeDeepLinkDataSource()
        playback = RecordingPlaybackController()
        clock = Date(timeIntervalSince1970: 1_761_782_400)
    }

    private func makeRouter(hosts: [String] = ["example.com"], mainActive: Bool = true) async -> DeepLinkRouter {
        let router = DeepLinkRouter(hosts: hosts, dataSource: source, playback: playback, now: { [unowned self] in clock })
        if mainActive {
            await router.mainDidAppear()
        }
        return router
    }

    private func url(_ text: String) -> URL {
        URL(string: text)!
    }

    private static let route123 = PodcastRoute(feedId: 123, title: "Podcast 123", summary: "", imageURL: nil)

    private static func episodeTarget(id: Int64 = 456, feedId: Int64 = 123) -> DeepLinkTarget {
        let route = PodcastRoute(feedId: feedId, title: "Podcast \(feedId)", summary: "", imageURL: nil)
        let request = PlaybackRequest(podcast: route, episodes: [EpisodeRow.sample(id)], startIndex: 0)
        return .episode(request: request, route: route)
    }

    func testPodcastLinkOpensDetailOnTheDiscoverTab() async {
        let router = await makeRouter()
        router.navigation.selectedTab = .search
        router.navigation.isShowingSettings = true
        source.targets[.podcast(feedId: 123)] = .podcast(Self.route123)
        var revealed = 0
        router.willReveal = { revealed += 1 }

        await router.handle(url("https://example.com/podcast/123-ten-podcast"))

        XCTAssertEqual(source.resolved, [.podcast(feedId: 123)])
        XCTAssertEqual(router.navigation.selectedTab, .home, "Khám phá is selected")
        XCTAssertEqual(router.navigation.homePath, [Self.route123])
        XCTAssertFalse(router.navigation.isShowingSettings, "the Settings sheet closes")
        XCTAssertEqual(revealed, 1, "the Player is closed before the route shows")
        XCTAssertTrue(playback.requests.isEmpty)
        XCTAssertFalse(router.isShowingError)
    }

    func testPodcastLinkPushesOnTopOfTheCurrentDiscoverStack() async {
        let router = await makeRouter()
        let earlier = PodcastRoute(feedId: 7, title: "Earlier", summary: "", imageURL: nil)
        router.navigation.homePath = [earlier]
        source.targets[.podcast(feedId: 123)] = .podcast(Self.route123)

        await router.handle(url("https://example.com/en/podcast/123/"))

        XCTAssertEqual(router.navigation.homePath, [earlier, Self.route123])
    }

    func testEpisodeLinkPlaysThroughE12AndOpensDetailOfTheFeed() async throws {
        let router = await makeRouter()
        let target = Self.episodeTarget()
        source.targets[.episode(id: 456)] = target

        await router.handle(url("https://example.com/episode/456-tap-1"))

        XCTAssertEqual(source.resolved, [.episode(id: 456)], "E12 through the data source")
        XCTAssertEqual(playback.requests.count, 1, "exactly one request")
        XCTAssertEqual(playback.requests.first?.startItem?.id, "456")
        XCTAssertEqual(router.navigation.selectedTab, .home)
        XCTAssertEqual(router.navigation.homePath.map(\.feedId), [123], "Detail of the episode's feed")
    }

    func testLinkThatCannotBeOpenedShowsTheError() async {
        let router = await makeRouter()

        await router.handle(url("https://example.com/episode/999-x"))

        XCTAssertEqual(source.resolved, [.episode(id: 999)])
        XCTAssertTrue(router.isShowingError, "deeplink.error alert")
        XCTAssertTrue(playback.requests.isEmpty)
        XCTAssertTrue(router.navigation.homePath.isEmpty)
    }

    func testLinkDuringOnboardingIsHeldAndOpenedOnceWhenMainAppears() async {
        let router = await makeRouter(mainActive: false)
        source.targets[.podcast(feedId: 123)] = .podcast(Self.route123)

        await router.handle(url("https://example.com/podcast/123-x"))

        XCTAssertEqual(router.pending, .podcast(feedId: 123))
        XCTAssertTrue(source.resolved.isEmpty, "nothing opens before Main")
        XCTAssertTrue(router.navigation.homePath.isEmpty)

        await router.mainDidAppear()
        await router.mainDidAppear()

        XCTAssertEqual(source.resolved, [.podcast(feedId: 123)], "opened exactly once")
        XCTAssertEqual(router.navigation.homePath, [Self.route123])
        XCTAssertNil(router.pending)
    }

    func testOnlyTheLatestHeldLinkIsKept() async {
        let router = await makeRouter(mainActive: false)
        source.targets[.episode(id: 456)] = Self.episodeTarget()

        await router.handle(url("https://example.com/podcast/123-x"))
        await router.handle(url("https://example.com/episode/456-x"))
        await router.mainDidAppear()

        XCTAssertEqual(source.resolved, [.episode(id: 456)])
        XCTAssertEqual(playback.requests.count, 1)
    }

    func testNoConfiguredHostIgnoresEveryLink() async {
        for hosts in [[], [""], ["  "]] {
            let router = await makeRouter(hosts: hosts)

            await router.handle(url("https://example.com/podcast/123-x"))
            await router.handle(url("https://example.com/episode/456-x"))

            XCTAssertTrue(source.resolved.isEmpty, "hosts \(hosts)")
            XCTAssertNil(router.pending)
            XCTAssertFalse(router.isShowingError, "an ignored link shows no alert")
        }
        XCTAssertTrue(playback.requests.isEmpty)
    }

    func testLinksThatAreNotOursAreIgnoredSilently() async {
        let router = await makeRouter()
        let foreign = [
            "https://other.com/podcast/123-x",
            "http://example.com/podcast/123-x",
            "tss://ts.podcast/playing?mediaId=1",
            "https://example.com/category/x",
            "https://example.com/podcast/abc-123",
            "https://example.com/podcast/0",
        ]
        for text in foreign {
            await router.handle(url(text))
        }

        XCTAssertTrue(source.resolved.isEmpty)
        XCTAssertFalse(router.isShowingError)
    }

    func testHostIsMatchedWithoutCase() async {
        let router = await makeRouter(hosts: ["Example.COM"])

        XCTAssertEqual(router.link(for: url("https://EXAMPLE.com/podcast/123-x")), .podcast(feedId: 123))
        XCTAssertEqual(router.link(for: url("https://example.com/episode/%34%35%36-x")), .episode(id: 456))
    }

    /// `.onOpenURL` and `.onContinueUserActivity` delivering the same open must not push twice.
    func testTheSameURLTwiceInARowOpensOnce() async {
        let router = await makeRouter()
        source.targets[.podcast(feedId: 123)] = .podcast(Self.route123)
        let link = url("https://example.com/podcast/123-x")

        await router.handle(link)
        await router.handle(link)
        XCTAssertEqual(router.navigation.homePath.count, 1)

        clock += DeepLinkRouter.duplicateWindow + 1
        await router.handle(link)
        XCTAssertEqual(router.navigation.homePath.count, 2, "a later tap on the same link opens again")
    }

    /// A slow E12 answer for an older link must not override a newer link.
    func testAnOlderResolutionThatFinishesLateIsDropped() async {
        let router = await makeRouter()
        source.targets[.episode(id: 456)] = Self.episodeTarget()
        source.targets[.podcast(feedId: 123)] = .podcast(Self.route123)
        source.gated = [.episode(id: 456)]

        let slow = Task { await router.handle(url("https://example.com/episode/456-x")) }
        await source.waitUntilResolving(.episode(id: 456))
        await router.handle(url("https://example.com/podcast/123-x"))
        source.release()
        await slow.value

        XCTAssertEqual(router.navigation.homePath, [Self.route123])
        XCTAssertTrue(playback.requests.isEmpty, "the older episode link does not play")
    }

    func testHostComesFromTSAppLinkHost() {
        XCTAssertEqual(AppLinkHost.hosts(from: [:]), [])
        XCTAssertEqual(AppLinkHost.hosts(from: ["TSAppLinkHost": ""]), [])
        XCTAssertEqual(AppLinkHost.hosts(from: ["TSAppLinkHost": " example.com "]), ["example.com"])
    }

    /// The app ships with `TSAppLinkHost = $(APP_LINK_HOST)`, empty by default: no link is accepted.
    func testTheAppHasNoHostByDefault() {
        XCTAssertNotNil(Bundle.main.object(forInfoDictionaryKey: AppLinkHost.infoKey), "Info.plist declares TSAppLinkHost")
        XCTAssertEqual(AppLinkHost.hosts(from: Bundle.main.infoDictionary ?? [:]), [])
    }

    /// The real data source maps an E12 episode like a favourite tap: one-item playlist saved with
    /// its podcast, Detail of the feed.
    func testSharedEpisodeTargetBuildsTheRequestAndTheRoute() throws {
        let podcast = SharedModelFactory.podcast(123, title: "Show")
        let episode = SharedModelFactory.episode(456, feedId: 123)

        let target = SharedDeepLinkDataSource.episodeTarget(podcast: podcast, episode: episode)

        guard case .episode(let request, let route) = target else { return XCTFail("episode target expected") }
        XCTAssertEqual(route.feedId, 123)
        XCTAssertEqual(route.title, "Show")
        XCTAssertEqual(request.items.map(\.id), ["456"])
        XCTAssertEqual(request.startIndex, 0)
        XCTAssertEqual(request.history, .playlist(podcast: podcast, episodes: [episode]))
    }
}

/// `DeepLinkDataSource` that records every resolve; `gated` links wait for `release()`.
@MainActor
final class FakeDeepLinkDataSource: DeepLinkDataSource {
    var targets: [AppLink: DeepLinkTarget] = [:]
    private(set) var resolved: [AppLink] = []
    var gated: Set<AppLink> = []
    private var waiting: [CheckedContinuation<Void, Never>] = []
    private var resolvingWaiters: [(AppLink, CheckedContinuation<Void, Never>)] = []

    func resolve(_ link: AppLink) async -> DeepLinkTarget? {
        resolved.append(link)
        resolvingWaiters.removeAll { pair in
            guard pair.0 == link else { return false }
            pair.1.resume()
            return true
        }
        if gated.contains(link) {
            await withCheckedContinuation { waiting.append($0) }
        }
        return targets[link]
    }

    func waitUntilResolving(_ link: AppLink) async {
        if resolved.contains(link) { return }
        await withCheckedContinuation { resolvingWaiters.append((link, $0)) }
    }

    func release() {
        let continuations = waiting
        waiting = []
        continuations.forEach { $0.resume() }
    }
}
