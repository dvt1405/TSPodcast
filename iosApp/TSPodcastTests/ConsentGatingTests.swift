import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M6-AC13 / AC19: the gated analytics and crash seams pass nothing on while consent is off.
@MainActor
final class ConsentGatingTests: XCTestCase {
    func testAnalyticsReachTheTrackerOnlyWhenAllowed() {
        let inner = RecordingAnalyticsTracker()
        let gate = ConsentGate()
        let tracker = ConsentGatedAnalyticsTracker(inner: inner, gate: gate)

        tracker.trackEvent(name: "show_hidden", screen: "PodcastDetail", params: ["feed_id": "1"])
        tracker.trackScreen(screen: "Onboarding", params: [:])
        XCTAssertTrue(inner.events.isEmpty)
        XCTAssertTrue(inner.screens.isEmpty)

        gate.update(analytics: true, crash: false)
        tracker.trackEvent(name: "show_hidden", screen: "PodcastDetail", params: ["feed_id": "1"])
        tracker.trackScreen(screen: "Onboarding", params: [:])
        XCTAssertEqual(inner.events.map(\.name), ["show_hidden"])
        XCTAssertEqual(inner.screens.map(\.screen), ["Onboarding"])
    }

    func testCrashReportsReachCrashlyticsOnlyWhenAllowed() {
        let inner = RecordingCrashReporter()
        let local = RecordingCrashReporter()
        let gate = ConsentGate(analytics: true, crash: false)
        let reporter = ConsentGatedCrashReporter(inner: inner, local: local, gate: gate)
        let error = KotlinIllegalStateException(message: "boom")

        reporter.log(message: "crumb")
        reporter.record(throwable: error, tag: "Test", keys: [:])
        reporter.userId(id: "install-id")
        XCTAssertTrue(inner.logs.isEmpty)
        XCTAssertTrue(inner.records.isEmpty)
        XCTAssertTrue(inner.userIds.isEmpty)
        XCTAssertEqual(local.logs, ["crumb"], "kept on the device")
        XCTAssertEqual(local.records.count, 1)
        XCTAssertTrue(local.userIds.isEmpty, "the user id never goes anywhere while off")

        gate.update(analytics: true, crash: true)
        reporter.record(throwable: error, tag: "Test", keys: [:])
        reporter.userId(id: "install-id")
        XCTAssertEqual(inner.records.count, 1)
        XCTAssertEqual(inner.userIds, ["install-id"])
    }

    func testEnvironmentAnalyticsIsGated() {
        let fixture = SharedGraphFixture()
        let inner = RecordingAnalyticsTracker()
        let services = AppServices(
            analytics: inner,
            crashReporter: RecordingCrashReporter(),
            config: NoOpConfig(),
            push: FakePushRegistrar(),
            remoteConfig: nil
        )
        let environment = AppEnvironment(launchOptions: .none, artworkLoader: .shared, graph: fixture.graph, pruner: NoOpPruner(), services: services)
        defer {
            environment.audioEngine.shutdown()
            fixture.close()
        }

        XCTAssertTrue(environment.analytics is ConsentGatedAnalyticsTracker)
        environment.analytics.trackEvent(name: "content_report", screen: nil, params: [:])
        XCTAssertTrue(inner.events.isEmpty, "nothing before the consent is read and given")
    }
}
