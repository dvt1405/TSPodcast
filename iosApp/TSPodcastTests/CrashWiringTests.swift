import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M4-AC16: crash wiring at launch (Android `App.onCreate`: `Crash.install` →
/// `Crash.userId(getOrCreateInstallId())`), the Kotlin hook after Firebase, and the shared facade.
@MainActor
final class CrashWiringTests: XCTestCase {
    /// Firebase → services → `Crash.install` → graph, then the crash user id = shared Install ID,
    /// sent exactly once.
    func testLaunchOrderAndInstallIdAsUserId() async throws {
        let fixture = SharedGraphFixture()
        let reporter = RecordingCrashReporter()
        var steps: [String] = []
        let launch = LaunchSteps(
            configureFirebase: {
                steps.append("firebase.configure")
                return .enabled
            },
            makeServices: { _ in
                steps.append("services")
                return AppServices(
                    analytics: RecordingAnalyticsTracker(),
                    crashReporter: reporter,
                    config: NoOpConfig(),
                    push: FakePushRegistrar(),
                    remoteConfig: nil
                )
            },
            installCrashReporter: { installed in
                steps.append("crash.install")
                XCTAssertTrue(installed === reporter)
            },
            makeGraph: { _, _ in
                steps.append("graph")
                return (fixture.graph, NoOpPruner())
            }
        )
        let environment = AppEnvironment(launchOptions: .none, artworkLoader: .shared, launch: launch)
        defer {
            environment.audioEngine.shutdown()
            fixture.close()
        }

        XCTAssertEqual(steps, ["firebase.configure", "services", "crash.install", "graph"],
                       "the reporter is installed before any shared object exists")
        await waitUntil("user id sent") { !reporter.userIds.isEmpty }
        try await Task.sleep(for: .milliseconds(300))

        let installId = try await fixture.graph.installId.getOrCreate()
        XCTAssertEqual(reporter.userIds, [installId], "exactly once, the Install ID")
        XCTAssertNotNil(UUID(uuidString: installId), "a random UUID, not a device identifier")
    }

    /// `CrashWiring.install`: the shared `Crash` first, then the Kotlin hook on it.
    func testInstallSetsSharedCrashThenKotlinHook() {
        let reporter = RecordingCrashReporter()
        var calls: [String] = []
        CrashWiring.install(
            reporter,
            installShared: { installed in
                calls.append("crash.install")
                XCTAssertTrue(installed === reporter)
            },
            installKotlinHook: { calls.append("kotlin.hook") }
        )
        XCTAssertEqual(calls, ["crash.install", "kotlin.hook"])
    }

    /// After `Crash.install`, shared code reporting through the facade reaches the reporter.
    func testSharedFacadeForwardsToTheInstalledReporter() {
        let reporter = RecordingCrashReporter()
        Crash.shared.install(reporter: reporter)
        defer { Crash.shared.install(reporter: OSLogCrashReporter()) }

        Crash.shared.log(message: "breadcrumb")
        Crash.shared.record(throwable: KotlinIllegalStateException(message: "boom"), tag: "Test", keys: ["medium": "music"])
        Crash.shared.userId(id: "abc")

        XCTAssertEqual(reporter.logs, ["breadcrumb"])
        XCTAssertEqual(reporter.records.map(\.tag), ["Test"])
        XCTAssertEqual(reporter.records.first?.keys, ["medium": "music"])
        XCTAssertEqual(reporter.records.first?.message, "boom")
        XCTAssertEqual(reporter.userIds, ["abc"])
    }

    /// Crashlytics non-fatals group by call site: the error domain is the `safeCall` tag.
    func testNonFatalErrorUsesTheTagAsDomain() {
        let error = CrashWiring.error(for: KotlinIllegalStateException(message: "boom"), tag: "GetMusicFeeds")
        XCTAssertEqual(error.domain, "GetMusicFeeds")
        XCTAssertEqual(error.code, 0)
        XCTAssertTrue(error.localizedDescription.contains("boom"))
        XCTAssertTrue(error.localizedDescription.contains("IllegalStateException"))
    }
}
