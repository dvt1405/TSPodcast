import XCTest
@testable import TSPodcast

/// `ConsentStore` in memory; counts saves.
final class FakeConsentStore: ConsentStore, @unchecked Sendable {
    private let lock = NSLock()
    private var current: ConsentSnapshot
    private var _saves: [(analytics: Bool, crash: Bool)] = []

    init(_ snapshot: ConsentSnapshot = .undecided) {
        current = snapshot
    }

    var saves: [(analytics: Bool, crash: Bool)] { lock.withLock { _saves } }

    func state() async -> ConsentSnapshot { lock.withLock { current } }

    func save(analytics: Bool, crash: Bool) async -> ConsentSnapshot {
        lock.withLock {
            _saves.append((analytics, crash))
            current = ConsentSnapshot(analytics: analytics, crash: crash, needsPrompt: false, decidedAt: Date(timeIntervalSince1970: 1_761_800_000))
            return current
        }
    }
}

/// `CollectionSwitch` that records every call (stands for Firebase Analytics / Crashlytics).
final class RecordingCollectionSwitch: CollectionSwitch, @unchecked Sendable {
    private let lock = NSLock()
    private var _analytics: [Bool] = []
    private var _crash: [Bool] = []

    var analytics: [Bool] { lock.withLock { _analytics } }
    var crash: [Bool] { lock.withLock { _crash } }

    func setAnalyticsCollectionEnabled(_ enabled: Bool) { lock.withLock { _analytics.append(enabled) } }
    func setCrashCollectionEnabled(_ enabled: Bool) { lock.withLock { _crash.append(enabled) } }
}

/// M6-AC13 (plain Swift part, also run with `swift test` on macOS): the controller reads the
/// saved choice, applies it to the gate and the SDK switches, shows the sheet when needed and
/// applies every change at once.
@MainActor
final class ConsentControllerTests: XCTestCase {
    private func makeController(
        _ store: FakeConsentStore,
        gate: ConsentGate = ConsentGate(),
        collection: RecordingCollectionSwitch = RecordingCollectionSwitch(),
        onCrash: @escaping @MainActor () -> Void = {}
    ) -> ConsentController {
        ConsentController(store: store, gate: gate, collection: collection, onCrashReportsAllowed: onCrash)
    }

    func testGateStartsClosed() {
        let gate = ConsentGate()
        XCTAssertFalse(gate.allowsAnalytics)
        XCTAssertFalse(gate.allowsCrashReports)
    }

    func testFreshInstallShowsTheSheetAndKeepsEverythingOff() async {
        let gate = ConsentGate()
        let collection = RecordingCollectionSwitch()
        let controller = makeController(FakeConsentStore(), gate: gate, collection: collection)

        await controller.load()

        XCTAssertTrue(controller.isPromptPresented)
        XCTAssertFalse(gate.allowsAnalytics)
        XCTAssertFalse(gate.allowsCrashReports)
        XCTAssertEqual(collection.analytics, [false])
        XCTAssertEqual(collection.crash, [false])
    }

    func testSavedChoiceIsAppliedAtLaunchWithoutTheSheet() async {
        let gate = ConsentGate()
        let collection = RecordingCollectionSwitch()
        var crashAllowed = 0
        let store = FakeConsentStore(ConsentSnapshot(analytics: true, crash: true, needsPrompt: false, decidedAt: Date()))
        let controller = makeController(store, gate: gate, collection: collection) { crashAllowed += 1 }

        await controller.load()
        await controller.load()

        XCTAssertFalse(controller.isPromptPresented)
        XCTAssertTrue(gate.allowsAnalytics)
        XCTAssertTrue(gate.allowsCrashReports)
        XCTAssertEqual(collection.analytics, [true], "loaded once")
        XCTAssertEqual(crashAllowed, 1, "the crash user id is sent once crash reports are allowed")
    }

    func testAcceptAllClosesTheSheetAndOpensEverything() async {
        let gate = ConsentGate()
        let store = FakeConsentStore()
        let controller = makeController(store, gate: gate)
        await controller.load()

        await controller.acceptAll()

        XCTAssertFalse(controller.isPromptPresented)
        XCTAssertTrue(gate.allowsAnalytics)
        XCTAssertTrue(gate.allowsCrashReports)
        XCTAssertEqual(store.saves.map(\.analytics), [true])
        XCTAssertNotNil(controller.snapshot.decidedAt)
    }

    func testNecessaryOnlyIsADecisionThatGoesOn() async {
        let gate = ConsentGate()
        let store = FakeConsentStore()
        let controller = makeController(store, gate: gate)
        await controller.load()

        await controller.saveChoice(analytics: false, crash: false)

        XCTAssertFalse(controller.isPromptPresented, "the app goes on")
        XCTAssertFalse(controller.snapshot.needsPrompt)
        XCTAssertFalse(gate.allowsAnalytics)
        XCTAssertFalse(gate.allowsCrashReports)
        XCTAssertEqual(store.saves.count, 1)
    }

    func testSheetTogglesAreSavedAsChosen() async {
        let gate = ConsentGate()
        let controller = makeController(FakeConsentStore(), gate: gate)
        await controller.load()

        await controller.saveChoice(analytics: true, crash: false)

        XCTAssertTrue(gate.allowsAnalytics)
        XCTAssertFalse(gate.allowsCrashReports)
    }

    func testSettingsTogglesApplyAtOnce() async {
        let gate = ConsentGate()
        let collection = RecordingCollectionSwitch()
        var crashAllowed = 0
        let controller = makeController(FakeConsentStore(), gate: gate, collection: collection) { crashAllowed += 1 }
        await controller.load()

        await controller.setAnalytics(true)
        XCTAssertTrue(gate.allowsAnalytics)
        XCTAssertFalse(gate.allowsCrashReports)

        await controller.setCrashReports(true)
        XCTAssertTrue(gate.allowsCrashReports)
        XCTAssertEqual(crashAllowed, 1)

        await controller.setAnalytics(false)
        XCTAssertFalse(gate.allowsAnalytics)
        XCTAssertTrue(gate.allowsCrashReports, "the other choice is kept")
        XCTAssertEqual(collection.analytics.last, false)
        XCTAssertEqual(collection.crash.last, true)
    }
}
