import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M4-AC17: the launch Remote Config fetch (Android `TSFirebaseRemoteConfig.setup`,
/// features-spec §1: up to 20 tries, 30 s × n backoff), the blacklist reload, and the bundled
/// asset without Firebase.
@MainActor
final class RemoteConfigLoaderTests: XCTestCase {
    /// Fails `failures` times, then activates `values`.
    private final class FakeSource: RemoteConfigSource, @unchecked Sendable {
        private let lock = NSLock()
        private var remainingFailures: Int
        private var pending: [String: String]
        private var active: [String: String] = [:]
        private var _fetches = 0

        init(failures: Int, values: [String: String] = [:]) {
            remainingFailures = failures
            pending = values
        }

        var fetches: Int { lock.withLock { _fetches } }

        func fetchAndActivate() async throws {
            try lock.withLock {
                _fetches += 1
                if remainingFailures > 0 {
                    remainingFailures -= 1
                    throw URLError(.notConnectedToInternet)
                }
                active = pending
            }
        }

        func string(forKey key: String) -> String? {
            lock.withLock { active[key] }
        }
    }

    private final class Recorder: @unchecked Sendable {
        private let lock = NSLock()
        private var _sleeps: [Duration] = []
        private var _reloads = 0
        var sleeps: [Duration] { lock.withLock { _sleeps } }
        var reloads: Int { lock.withLock { _reloads } }
        func sleep(_ duration: Duration) { lock.withLock { _sleeps.append(duration) } }
        func reload() { lock.withLock { _reloads += 1 } }
    }

    private func makeLoader(_ source: FakeSource, _ recorder: Recorder, crash: RecordingCrashReporter) -> RemoteConfigLoader {
        RemoteConfigLoader(
            source: source,
            crash: crash,
            onActivated: { recorder.reload() },
            sleep: { recorder.sleep($0) }
        )
    }

    func testRetriesWithLinearBackoffThenReloadsOnce() async {
        let source = FakeSource(failures: 2)
        let recorder = Recorder()
        let crash = RecordingCrashReporter()

        let activated = await makeLoader(source, recorder, crash: crash).run()

        XCTAssertTrue(activated)
        XCTAssertEqual(source.fetches, 3)
        XCTAssertEqual(recorder.sleeps, [.seconds(30), .seconds(60)])
        XCTAssertEqual(recorder.reloads, 1)
        XCTAssertTrue(crash.records.isEmpty)
    }

    func testStopsAfterTwentyFailuresWithoutReload() async {
        let source = FakeSource(failures: 100)
        let recorder = Recorder()
        let crash = RecordingCrashReporter()

        let activated = await makeLoader(source, recorder, crash: crash).run()

        XCTAssertFalse(activated)
        XCTAssertEqual(source.fetches, 20, "RemoteConfigRetry.MAX_ATTEMPTS")
        XCTAssertEqual(recorder.sleeps.count, 19, "no wait after the last try")
        XCTAssertEqual(recorder.sleeps.first, .seconds(30))
        XCTAssertEqual(recorder.sleeps.last, .seconds(30 * 19))
        XCTAssertEqual(recorder.reloads, 0)
        XCTAssertEqual(crash.records.map(\.tag), [RemoteConfigLoader.crashTag])
        XCTAssertEqual(crash.records.first?.keys, ["attempts": "20"])
    }

    func testFirstTrySuccessDoesNotWait() async {
        let source = FakeSource(failures: 0)
        let recorder = Recorder()
        let activated = await makeLoader(source, recorder, crash: RecordingCrashReporter()).run()
        XCTAssertTrue(activated)
        XCTAssertEqual(recorder.sleeps, [])
        XCTAssertEqual(recorder.reloads, 1)
    }

    /// The fetched `ts_podcast_black_list` reaches shared through `ConfigAPI.getString`, and the
    /// graph's blacklist switches to it on the reload.
    func testFetchedBlacklistReplacesTheBundledOneAfterReload() async throws {
        let remote = #"{"feedId": [111, "222"], "filterText": ["casino"]}"#
        let source = FakeSource(failures: 1, values: [ConfigKeys.shared.KEY_BLACK_LIST: remote])
        let config = FirebaseRemoteConfigAPI(source: source)
        let files = SharedGraphFixture.Files.fresh("remote-config")
        let graph = SharedGraph(
            credentials: BundleCredentialsProvider(),
            config: config,
            preferencesPath: files.preferences.path(percentEncoded: false),
            databasePath: files.database.path(percentEncoded: false)
        )
        defer { graph.close() }
        let blacklist = graph.blacklistRepository
        XCTAssertNil(config.getString(key: ConfigKeys.shared.KEY_BLACK_LIST), "nothing activated yet")
        XCTAssertGreaterThan(blacklist.feedIdCount, 2, "bundled blacklist.json until the fetch")

        let loader = RemoteConfigLoader(
            source: source,
            crash: RecordingCrashReporter(),
            onActivated: { blacklist.reload() },
            sleep: { _ in }
        )
        let activated = await loader.run()

        XCTAssertTrue(activated)
        XCTAssertEqual(config.getString(key: ConfigKeys.shared.KEY_BLACK_LIST), remote)
        XCTAssertEqual(blacklist.feedIdCount, 2)
        XCTAssertTrue(blacklist.isInBlacklist(id: "111"))
        XCTAssertTrue(blacklist.isInBlacklist(id: "222"))
    }

    /// M6 §3.4: the app's `onActivated` (`SharedGraph.reloadRemoteConfig`) also reads
    /// `news_hub_v1` again, so a hub turned on remotely appears without a new launch.
    func testActivationReloadsTheNewsHubConfigToo() async throws {
        let remote = #"{"version":1,"enabled":true,"sections":[{"id":"news","sources":[{"key":"remote_news","feedUrl":"https://news.example.com/feed.rss","confirmedAt":"2026-11-02","brief":{"take":1}}]}]}"#
        let source = FakeSource(failures: 0, values: [ConfigKeys.shared.KEY_NEWS_HUB: remote])
        let files = SharedGraphFixture.Files.fresh("remote-config-news")
        let graph = SharedGraph(
            credentials: BundleCredentialsProvider(),
            config: FirebaseRemoteConfigAPI(source: source),
            preferencesPath: files.preferences.path(percentEncoded: false),
            databasePath: files.database.path(percentEncoded: false)
        )
        defer { graph.close() }
        XCTAssertFalse(graph.newsHubConfig.isEnabled, "bundled config: no source confirmed yet")

        let loader = RemoteConfigLoader(
            source: source,
            crash: RecordingCrashReporter(),
            onActivated: { graph.reloadRemoteConfig() },
            sleep: { _ in }
        )
        let activated = await loader.run()

        XCTAssertTrue(activated)
        XCTAssertTrue(graph.newsHubConfig.isEnabled)
        XCTAssertTrue(graph.newsHubConfig.usesRemoteValue)
        XCTAssertEqual(graph.newsHubConfig.config.newsSources.map(\.key), ["remote_news"])
    }

    /// Without Firebase (`NoOpConfig`) shared keeps the bundled asset (M0 behaviour).
    func testWithoutFirebaseTheBundledBlacklistIsUsed() {
        let fixture = SharedGraphFixture()
        defer { fixture.close() }
        XCTAssertNil(NoOpConfig().getString(key: ConfigKeys.shared.KEY_BLACK_LIST))
        XCTAssertGreaterThan(fixture.graph.blacklistRepository.feedIdCount, 0)
        XCTAssertGreaterThan(fixture.graph.blacklistRepository.keywordCount, 0)
    }

    func testCancelledWaitStopsTheLoop() async {
        let source = FakeSource(failures: 100)
        let loader = RemoteConfigLoader(
            source: source,
            crash: RecordingCrashReporter(),
            onActivated: {},
            sleep: { _ in throw CancellationError() }
        )
        let activated = await loader.run()
        XCTAssertFalse(activated)
        XCTAssertEqual(source.fetches, 1)
    }
}
