import Foundation
@preconcurrency import Shared

/// `AnalyticsTracker` that forwards only while the user allows usage analytics (M6 §3.6). Every
/// event of the app goes through it (`AppEnvironment.analytics`), whatever is behind it
/// (Firebase or the OSLog tracker).
final class ConsentGatedAnalyticsTracker: NSObject, AnalyticsTracker, @unchecked Sendable {
    let inner: any AnalyticsTracker
    private let gate: ConsentGate

    init(inner: any AnalyticsTracker, gate: ConsentGate) {
        self.inner = inner
        self.gate = gate
    }

    func trackScreen(screen: String, params: [String: String]) {
        guard gate.allowsAnalytics else { return }
        inner.trackScreen(screen: screen, params: params)
    }

    func trackEvent(name: String, screen: String?, params: [String: String]) {
        guard gate.allowsAnalytics else { return }
        inner.trackEvent(name: name, screen: screen, params: params)
    }
}

/// Shared `CrashReporter` installed as `Crash` (M6 §3.6): while the user has not allowed crash
/// reports, nothing reaches `inner` (Crashlytics). Records and breadcrumbs then go to `local`
/// (OSLog, never leaves the device), so the app is still debuggable; the user id and custom keys
/// are dropped.
final class ConsentGatedCrashReporter: NSObject, CrashReporter, @unchecked Sendable {
    let inner: any CrashReporter
    private let local: (any CrashReporter)?
    private let gate: ConsentGate

    init(inner: any CrashReporter, local: (any CrashReporter)?, gate: ConsentGate) {
        self.inner = inner
        self.local = local
        self.gate = gate
    }

    func log(message: String) {
        if gate.allowsCrashReports {
            inner.log(message: message)
        } else {
            local?.log(message: message)
        }
    }

    func record(throwable: KotlinThrowable, tag: String, keys: [String: String]) {
        if gate.allowsCrashReports {
            inner.record(throwable: throwable, tag: tag, keys: keys)
        } else {
            local?.record(throwable: throwable, tag: tag, keys: keys)
        }
    }

    func key(name: String, value: Bool) {
        guard gate.allowsCrashReports else { return }
        inner.key(name: name, value: value)
    }

    func key(name: String, value_ value: Int64) {
        guard gate.allowsCrashReports else { return }
        inner.key(name: name, value_: value)
    }

    func key(name: String, value__ value: String) {
        guard gate.allowsCrashReports else { return }
        inner.key(name: name, value__: value)
    }

    func userId(id: String) {
        guard gate.allowsCrashReports else { return }
        inner.userId(id: id)
    }

    func recordFatal(throwable: KotlinThrowable, stackAddresses: [KotlinLong]) {
        if gate.allowsCrashReports {
            inner.recordFatal(throwable: throwable, stackAddresses: stackAddresses)
        } else {
            local?.recordFatal(throwable: throwable, stackAddresses: stackAddresses)
        }
    }
}
