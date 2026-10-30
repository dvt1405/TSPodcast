import XCTest
@testable import TSPodcast

/// M6-AC20: what the three App Shortcuts do, with a recording player and history (no
/// AVFoundation, no shared module, so the file also runs in the macOS host package).
@MainActor
final class ShortcutActionsTests: XCTestCase {
    @MainActor
    private final class FakePlayer: ShortcutPlayer {
        enum Call: Equatable {
            case play
            case start(String)
            case pause
            case sleepTimer(SleepTimerSetting, origin: String)
        }

        var hasCurrentItem = false
        private(set) var calls: [Call] = []

        func play() { calls.append(.play) }
        func start(_ resume: String) {
            calls.append(.start(resume))
            hasCurrentItem = true
        }
        func pause() { calls.append(.pause) }
        func setSleepTimer(_ setting: SleepTimerSetting, origin: String) {
            calls.append(.sleepTimer(setting, origin: origin))
        }
    }

    @MainActor
    private final class FakeHistory: ShortcutHistory {
        var last: String?
        /// Runs while the history is read (something else starts playing meanwhile).
        var whileReading: (() -> Void)?
        private(set) var reads = 0

        func lastPlayed() async -> String? {
            reads += 1
            whileReading?()
            return last
        }
    }

    private struct Event: Equatable {
        let name: String
        let params: [String: String]
    }

    /// The player, the history, the events and the handler under test, fresh for each test.
    @MainActor
    private final class Fixture {
        static let events = ShortcutEvents(invoked: "shortcut_invoked", intentParameter: "intent", sleepTimerOrigin: "shortcut")

        let player = FakePlayer()
        let history = FakeHistory()
        private(set) var logged: [Event] = []
        private(set) lazy var actions = ShortcutActionHandler(player: player, history: history, events: Self.events) { [unowned self] name, params in
            logged.append(Event(name: name, params: params))
        }
    }

    private func invoked(_ intent: String) -> Event {
        Event(name: "shortcut_invoked", params: ["intent": intent])
    }

    func testContinueWithAnItemPlaysItWithoutReadingTheHistory() async {
        let fixture = Fixture()
        fixture.player.hasCurrentItem = true
        fixture.history.last = "episode-1"

        let outcome = await fixture.actions.continueListening()

        XCTAssertEqual(outcome, .resumed)
        XCTAssertEqual(fixture.player.calls, [.play])
        XCTAssertEqual(fixture.history.reads, 0)
        XCTAssertEqual(fixture.logged, [invoked("continue_listening")])
    }

    func testContinueWithoutAnItemStartsTheLastSavedEpisode() async {
        let fixture = Fixture()
        fixture.history.last = "episode-7"

        let outcome = await fixture.actions.continueListening()

        XCTAssertEqual(outcome, .started)
        XCTAssertEqual(fixture.player.calls, [.start("episode-7")])
        XCTAssertEqual(fixture.logged, [invoked("continue_listening")])
    }

    func testContinueWithNothingSavedReportsNothingToResume() async {
        let fixture = Fixture()

        let outcome = await fixture.actions.continueListening()

        XCTAssertEqual(outcome, .nothingToResume)
        XCTAssertEqual(fixture.player.calls, [])
        XCTAssertEqual(fixture.logged, [invoked("continue_listening")], "logged whatever the outcome")
    }

    func testContinuePlaysWhatStartedWhileTheHistoryWasRead() async {
        let fixture = Fixture()
        let player = fixture.player
        fixture.history.last = "episode-7"
        fixture.history.whileReading = { player.hasCurrentItem = true }

        let outcome = await fixture.actions.continueListening()

        XCTAssertEqual(outcome, .resumed)
        XCTAssertEqual(player.calls, [.play], "the newer item is not replaced")
    }

    func testPauseIsTheUsersPause() {
        let fixture = Fixture()
        fixture.player.hasCurrentItem = true

        XCTAssertEqual(fixture.actions.pause(), .paused)

        XCTAssertEqual(fixture.player.calls, [.pause])
        XCTAssertEqual(fixture.logged, [invoked("pause")])
    }

    func testPauseWithoutAnItemDoesNothingButLogs() {
        let fixture = Fixture()

        XCTAssertEqual(fixture.actions.pause(), .nothingPlaying)

        XCTAssertEqual(fixture.player.calls, [])
        XCTAssertEqual(fixture.logged, [invoked("pause")])
    }

    func testSleepTimerSetsTheMinutesWithTheShortcutOrigin() {
        let fixture = Fixture()

        XCTAssertEqual(fixture.actions.setSleepTimer(minutes: 45), 45)

        XCTAssertEqual(fixture.player.calls, [.sleepTimer(.minutes(45), origin: "shortcut")])
        XCTAssertEqual(fixture.logged, [invoked("set_sleep_timer")])
    }

    func testSleepTimerMinutesAreClampedToOneThroughOneEighty() {
        let fixture = Fixture()
        let actions = fixture.actions

        XCTAssertEqual(actions.setSleepTimer(minutes: 0), 1)
        XCTAssertEqual(actions.setSleepTimer(minutes: -5), 1)
        XCTAssertEqual(actions.setSleepTimer(minutes: 500), 180)
        XCTAssertEqual(actions.setSleepTimer(minutes: 180), 180)

        XCTAssertEqual(fixture.player.calls, [
            .sleepTimer(.minutes(1), origin: "shortcut"),
            .sleepTimer(.minutes(1), origin: "shortcut"),
            .sleepTimer(.minutes(180), origin: "shortcut"),
            .sleepTimer(.minutes(180), origin: "shortcut"),
        ])
        XCTAssertEqual(fixture.logged.count, 4, "one shortcut_invoked per run")
    }

    func testIntentNamesAreStable() {
        XCTAssertEqual(ShortcutIntentKind.allCases.map(\.rawValue), ["continue_listening", "pause", "set_sleep_timer"])
    }
}
