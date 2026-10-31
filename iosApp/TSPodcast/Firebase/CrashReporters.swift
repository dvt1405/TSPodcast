import FirebaseCrashlytics
import Foundation
import OSLog
@preconcurrency import Shared

/// Shared `CrashReporter` without Firebase: everything goes to OSLog (category `Crash`). Named
/// `OSLogCrashReporter` because shared already exports a `LogCrashReporter` (its `println`
/// fallback used before `Crash.install`). Ids and types only, never payloads or URLs.
final class OSLogCrashReporter: NSObject, CrashReporter, Sendable {
    func log(message: String) {
        AppLog.crash.notice("crash_log \(message, privacy: .public)")
    }

    func record(throwable: KotlinThrowable, tag: String, keys: [String: String]) {
        let type = CrashWiring.typeName(of: throwable)
        AppLog.crash.error("crash_record tag=\(tag, privacy: .public) type=\(type, privacy: .public) keys=\(keys.count, privacy: .public)")
    }

    func key(name: String, value: Bool) {}

    func key(name: String, value_ value: Int64) {}

    func key(name: String, value__ value: String) {}

    func userId(id: String) {
        // The Install ID is random per install (D5), but it still stays out of the system log.
        AppLog.crash.notice("crash_user_id set")
    }

    func recordFatal(throwable: KotlinThrowable, stackAddresses: [KotlinLong]) {
        let type = CrashWiring.typeName(of: throwable)
        AppLog.crash.fault("crash_fatal type=\(type, privacy: .public) frames=\(stackAddresses.count, privacy: .public)")
    }
}

/// Shared `CrashReporter` on Crashlytics (Android `CrashReporter` implementation): breadcrumbs,
/// non-fatals grouped by `tag`, custom keys and the Install ID as user id. `recordFatal` turns an
/// uncaught Kotlin exception into a Crashlytics exception model with its raw stack addresses,
/// which Crashlytics symbolicates with the dSYM (plan §7 item 7).
final class FirebaseCrashReporter: NSObject, CrashReporter, Sendable {
    private var crashlytics: Crashlytics { Crashlytics.crashlytics() }

    func log(message: String) {
        crashlytics.log(message)
    }

    func record(throwable: KotlinThrowable, tag: String, keys: [String: String]) {
        crashlytics.record(error: CrashWiring.error(for: throwable, tag: tag), userInfo: keys)
    }

    func key(name: String, value: Bool) {
        crashlytics.setCustomValue(value, forKey: name)
    }

    func key(name: String, value_ value: Int64) {
        crashlytics.setCustomValue(value, forKey: name)
    }

    func key(name: String, value__ value: String) {
        crashlytics.setCustomValue(value, forKey: name)
    }

    func userId(id: String) {
        crashlytics.setUserID(id)
    }

    func recordFatal(throwable: KotlinThrowable, stackAddresses: [KotlinLong]) {
        let model = ExceptionModel(name: CrashWiring.typeName(of: throwable), reason: throwable.message ?? "")
        model.stackTrace = stackAddresses.map { StackFrame(address: UInt(bitPattern: Int($0.int64Value))) }
        crashlytics.record(exceptionModel: model)
    }
}

/// Steps 2 and 3 of the launch (Android `Crash.install(crashReporter)` then
/// `Crash.userId(getOrCreateInstallId())`), plus the Kotlin/Native hook.
enum CrashWiring {
    /// Installs `reporter` as the process-wide shared `Crash`, then the Kotlin crash hook on it
    /// (after Firebase is configured, so Crashlytics is ready when the hook fires). Runs before
    /// the `SharedGraph` exists, so every shared call already reports to it. Tests pass recording
    /// closures instead of the process-wide ones.
    static func install(
        _ reporter: any CrashReporter,
        installShared: (any CrashReporter) -> Void = { Crash.shared.install(reporter: $0) },
        installKotlinHook: () -> Void = { KotlinCrashHookKt.installKotlinCrashHook(reporter: Crash.shared) }
    ) {
        installShared(reporter)
        installKotlinHook()
    }

    /// Sends the shared Install ID as the crash user id (never a device identifier, D5).
    static func setUserId(installId: InstallId, reporter: any CrashReporter) async {
        do {
            let id = try await installId.getOrCreate()
            reporter.userId(id: id)
        } catch {
            AppLog.crash.error("install_id_failed type=\(String(describing: type(of: error)), privacy: .public)")
        }
    }

    /// Kotlin class name of `throwable` as Swift sees it (`KotlinIllegalStateException`, …).
    static func typeName(of throwable: KotlinThrowable) -> String {
        String(describing: type(of: throwable))
    }

    /// A non-fatal for Crashlytics: domain = `tag` so one call site groups as one issue.
    static func error(for throwable: KotlinThrowable, tag: String) -> NSError {
        NSError(domain: tag, code: 0, userInfo: [
            NSLocalizedDescriptionKey: "\(typeName(of: throwable)): \(throwable.message ?? "")",
        ])
    }
}
