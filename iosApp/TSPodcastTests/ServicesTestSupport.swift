import Foundation
import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// Records every call of shared `CrashReporter` (Swift side of the Kotlin protocol).
final class RecordingCrashReporter: NSObject, CrashReporter, @unchecked Sendable {
    struct Record {
        let message: String?
        let tag: String
        let keys: [String: String]
    }

    private let lock = NSLock()
    private var _logs: [String] = []
    private var _records: [Record] = []
    private var _userIds: [String] = []
    private var _fatals: [Int] = []

    var logs: [String] { lock.withLock { _logs } }
    var records: [Record] { lock.withLock { _records } }
    var userIds: [String] { lock.withLock { _userIds } }
    /// Stack frame counts of `recordFatal` calls.
    var fatals: [Int] { lock.withLock { _fatals } }

    func log(message: String) {
        lock.withLock { _logs.append(message) }
    }

    func record(throwable: KotlinThrowable, tag: String, keys: [String: String]) {
        lock.withLock { _records.append(Record(message: throwable.message, tag: tag, keys: keys)) }
    }

    func key(name: String, value: Bool) {}
    func key(name: String, value_ value: Int64) {}
    func key(name: String, value__ value: String) {}

    func userId(id: String) {
        lock.withLock { _userIds.append(id) }
    }

    func recordFatal(throwable: KotlinThrowable, stackAddresses: [KotlinLong]) {
        lock.withLock { _fatals.append(stackAddresses.count) }
    }
}

/// `PushRegistrar` that records topics.
final class FakePushRegistrar: PushRegistrar, @unchecked Sendable {
    private let lock = NSLock()
    private var _subscribed: [String] = []
    private var _unsubscribed: [String] = []
    private var _tokens: [Data] = []

    var subscribed: [String] { lock.withLock { _subscribed } }
    var unsubscribed: [String] { lock.withLock { _unsubscribed } }
    var tokens: [Data] { lock.withLock { _tokens } }

    func didRegister(deviceToken: Data) {
        lock.withLock { _tokens.append(deviceToken) }
    }

    func subscribe(topic: String) async {
        lock.withLock { _subscribed.append(topic) }
    }

    func unsubscribe(topic: String) async {
        lock.withLock { _unsubscribed.append(topic) }
    }
}

/// `NotificationSystem` with a scripted permission answer; counts every call.
@MainActor
final class FakeNotificationSystem: NotificationSystem {
    var status: NotificationAuthorization = .notDetermined
    /// What the iOS dialog answers.
    var grants = true
    private(set) var authorizationRequests = 0
    private(set) var remoteRegistrations = 0
    private(set) var settingsOpened = 0

    func authorization() async -> NotificationAuthorization { status }

    func requestAuthorization() async -> Bool {
        authorizationRequests += 1
        status = grants ? .authorized : .denied
        return grants
    }

    func registerForRemoteNotifications() {
        remoteRegistrations += 1
    }

    func openSettings() {
        settingsOpened += 1
    }
}

/// In-memory `NotificationPreferences`.
final class FakeNotificationPreferences: NotificationPreferences, @unchecked Sendable {
    private let lock = NSLock()
    private var _optIn: Bool
    private var _promptShown: Bool

    init(optIn: Bool = false, promptShown: Bool = false) {
        _optIn = optIn
        _promptShown = promptShown
    }

    var storedOptIn: Bool { lock.withLock { _optIn } }
    var storedPromptShown: Bool { lock.withLock { _promptShown } }

    func optIn() async -> Bool { storedOptIn }
    func setOptIn(_ value: Bool) async { lock.withLock { _optIn = value } }
    func promptShown() async -> Bool { storedPromptShown }
    func setPromptShown(_ value: Bool) async { lock.withLock { _promptShown = value } }
}

/// Launch pruner that does nothing (the launch tests check other steps).
struct NoOpPruner: LaunchCachePruning {
    func prune() async {}
}
