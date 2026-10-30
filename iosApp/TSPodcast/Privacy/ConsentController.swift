import Foundation
import Observation

/// Where the consent is saved; `SharedConsentStore` is the real one (shared `ConsentRepository`).
protocol ConsentStore: Sendable {
    func state() async -> ConsentSnapshot
    func save(analytics: Bool, crash: Bool) async -> ConsentSnapshot
}

/// The SDK switches behind the gate: Firebase Analytics / Crashlytics collection. Info.plist turns
/// both off (`FIREBASE_ANALYTICS_COLLECTION_ENABLED`, `FirebaseCrashlyticsCollectionEnabled`), so
/// nothing is collected until the controller turns them on after an opt-in. Without Firebase the
/// app uses `NoCollectionSwitch` and never calls a Firebase API.
protocol CollectionSwitch: Sendable {
    func setAnalyticsCollectionEnabled(_ enabled: Bool)
    func setCrashCollectionEnabled(_ enabled: Bool)
}

/// No SDK to switch (build without `GoogleService-Info.plist`).
struct NoCollectionSwitch: CollectionSwitch {
    func setAnalyticsCollectionEnabled(_ enabled: Bool) {}
    func setCrashCollectionEnabled(_ enabled: Bool) {}
}

/// Consent lite (M6 §3.6, N11, Apple 5.1.1(iv)): reads the saved choice at launch (before the
/// first screen), applies it to the gate and the SDKs, shows the first-launch sheet when needed
/// (never blocking: both buttons go on into the app) and applies the Settings toggles at once.
///
/// "Cần thiết" is not a choice: running the app, Remote Config (the spam block list) and the
/// random install id always run (PO decision, handoff).
@MainActor
@Observable
final class ConsentController {
    private(set) var snapshot: ConsentSnapshot = .undecided
    /// The first-launch sheet (`consent.sheet`); set by `load()` when a decision is needed.
    var isPromptPresented = false

    @ObservationIgnored let gate: ConsentGate
    @ObservationIgnored private let store: any ConsentStore
    @ObservationIgnored private let collection: any CollectionSwitch
    /// Runs when crash reports become allowed (the crash user id is sent again then).
    @ObservationIgnored private let onCrashReportsAllowed: @MainActor () -> Void
    @ObservationIgnored private var hasLoaded = false

    init(
        store: any ConsentStore,
        gate: ConsentGate,
        collection: any CollectionSwitch,
        onCrashReportsAllowed: @escaping @MainActor () -> Void = {}
    ) {
        self.store = store
        self.gate = gate
        self.collection = collection
        self.onCrashReportsAllowed = onCrashReportsAllowed
    }

    /// Reads the saved choice once and applies it; asks again when it is missing or older.
    func load() async {
        guard !hasLoaded else { return }
        hasLoaded = true
        apply(await store.state())
        isPromptPresented = snapshot.needsPrompt
        AppLog.app.notice("consent_loaded prompt=\(self.snapshot.needsPrompt, privacy: .public) analytics=\(self.snapshot.analytics, privacy: .public) crash=\(self.snapshot.crash, privacy: .public)")
    }

    /// "Đồng ý tất cả".
    func acceptAll() async {
        await save(analytics: true, crash: true)
    }

    /// "Chỉ cần thiết" / "Lưu lựa chọn" of the sheet: exactly the toggles as they are.
    func saveChoice(analytics: Bool, crash: Bool) async {
        await save(analytics: analytics, crash: crash)
    }

    /// Settings toggle "Phân tích sử dụng".
    func setAnalytics(_ allowed: Bool) async {
        await save(analytics: allowed, crash: snapshot.crash)
    }

    /// Settings toggle "Báo cáo sự cố".
    func setCrashReports(_ allowed: Bool) async {
        await save(analytics: snapshot.analytics, crash: allowed)
    }

    private func save(analytics: Bool, crash: Bool) async {
        // Apply first: a choice takes effect at once even if the write is slow.
        apply(ConsentSnapshot(analytics: analytics, crash: crash, needsPrompt: false, decidedAt: snapshot.decidedAt))
        isPromptPresented = false
        apply(await store.save(analytics: analytics, crash: crash))
        AppLog.app.notice("consent_saved analytics=\(analytics, privacy: .public) crash=\(crash, privacy: .public)")
    }

    private func apply(_ new: ConsentSnapshot) {
        let crashWasAllowed = gate.allowsCrashReports
        snapshot = new
        gate.update(analytics: new.analytics, crash: new.crash)
        collection.setAnalyticsCollectionEnabled(new.analytics)
        collection.setCrashCollectionEnabled(new.crash)
        if new.crash, !crashWasAllowed {
            onCrashReportsAllowed()
        }
    }
}
