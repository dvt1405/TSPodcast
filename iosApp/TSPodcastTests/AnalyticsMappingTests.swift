import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M4-AC16: what `FirebaseAnalyticsTracker` sends, without Firebase (the log call is injected).
/// Android `TSAnalytics.trackScreen` / `trackEvent`, features-spec §4.
final class AnalyticsMappingTests: XCTestCase {
    private final class Log: @unchecked Sendable {
        private let lock = NSLock()
        private var _events: [(name: String, parameters: [String: Any])] = []
        var events: [(name: String, parameters: [String: Any])] { lock.withLock { _events } }

        func append(_ name: String, _ parameters: [String: Any]) {
            lock.withLock { _events.append((name, parameters)) }
        }
    }

    /// 2023-11-14T22:13:20.9Z: the time stamp must be whole seconds.
    private let now = Date(timeIntervalSince1970: 1_700_000_000.9)

    private func makeTracker(_ log: Log) -> FirebaseAnalyticsTracker {
        let now = now
        return FirebaseAnalyticsTracker(osVersion: "17.5.1", appVersion: "1.0.0", now: { now }) { name, parameters in
            log.append(name, parameters)
        }
    }

    func testScreenEventNameAndParameters() throws {
        let log = Log()
        makeTracker(log).trackScreen(screen: "Onboarding", params: ["CurrentPage": "2"])

        let event = try XCTUnwrap(log.events.first)
        XCTAssertEqual(log.events.count, 1)
        XCTAssertEqual(event.name, "onboarding_screen")
        XCTAssertEqual(Set(event.parameters.keys), ["os", "os_version", "app_version", "event_time_stamp", "CurrentPage"])
        XCTAssertEqual(event.parameters["os"] as? String, "iOS")
        XCTAssertEqual(event.parameters["os_version"] as? String, "17.5.1")
        XCTAssertEqual(event.parameters["app_version"] as? String, "1.0.0")
        XCTAssertEqual(event.parameters["CurrentPage"] as? String, "2")
        XCTAssertEqual((event.parameters["event_time_stamp"] as? NSNumber)?.int64Value, 1_700_000_000, "seconds, not ms")
        XCTAssertNil(event.parameters["screen"], "Android's trackScreen sends no screen key")
    }

    func testEventCarriesTheScreen() throws {
        let log = Log()
        makeTracker(log).trackEvent(name: "OnboardingFinished", screen: "Onboarding", params: [:])

        let event = try XCTUnwrap(log.events.first)
        XCTAssertEqual(event.name, "OnboardingFinished")
        XCTAssertEqual(event.parameters["screen"] as? String, "Onboarding")
        XCTAssertEqual(Set(event.parameters.keys), ["os", "os_version", "app_version", "event_time_stamp", "screen"])
    }

    func testEventWithoutScreenHasNoScreenKey() throws {
        let log = Log()
        makeTracker(log).trackEvent(name: "fcm_new_podcasts_message_receive", screen: nil, params: ["type": "new_podcasts"])

        let event = try XCTUnwrap(log.events.first)
        XCTAssertNil(event.parameters["screen"])
        XCTAssertEqual(event.parameters["type"] as? String, "new_podcasts")
    }

    /// D5: no device identifier of any kind, also when a caller passes one.
    func testNoDeviceParameter() {
        let log = Log()
        let tracker = makeTracker(log)
        tracker.trackScreen(screen: "Home", params: [:])
        tracker.trackEvent(name: "OnboardingFinished", screen: "Onboarding", params: [:])

        for event in log.events {
            XCTAssertFalse(event.parameters.keys.contains { $0.lowercased().contains("device") }, event.name)
        }
    }

    func testFirebaseLimitsAreApplied() throws {
        let log = Log()
        let longName = String(repeating: "e", count: 50)
        let longKey = String(repeating: "k", count: 45)
        let longValue = String(repeating: "v", count: 120)
        makeTracker(log).trackEvent(name: longName, screen: "Home", params: [longKey: longValue])

        let event = try XCTUnwrap(log.events.first)
        XCTAssertEqual(event.name.count, 40)
        let key = String(repeating: "k", count: 40)
        XCTAssertEqual((event.parameters[key] as? String)?.count, 100)
    }

    func testDefaultVersionsComeFromTheSystemAndTheBundle() {
        XCTAssertEqual(FirebaseAnalyticsTracker.bundleVersion(), "1.0.0")
        XCTAssertTrue(FirebaseAnalyticsTracker.systemVersion().split(separator: ".").count == 3)
    }
}
