import AVFoundation
import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M6-AC4 / AC5 / AC6 / AC3 on the real engine: skip ±, speed per show, sleep timer, end of item
/// with and without auto-advance. Plays the 60 s fixture tone; the sleep timer runs on a pinned
/// clock so the test moves time by hand.
@MainActor
final class AudioEngineSpeedTimerTests: XCTestCase {
    private var clock: TestEpochClock!
    private var fixture: PlaybackFixture!

    override func setUp() async throws {
        clock = TestEpochClock()
        fixture = PlaybackFixture(clock: clock)
    }

    override func tearDown() async throws {
        fixture.close()
        fixture = nil
    }

    private var engine: AudioEngine { fixture.engine }
    private var events: AnalyticsEvents { AnalyticsEvents.shared }

    private func playAndWaitUntilReady(_ request: PlaybackRequest = PlaybackFixture.request()) async {
        engine.play(request)
        await waitUntil(timeout: 5, "playing") { engine.state.isPlaying && engine.state.durationMs > 0 }
    }

    private func autoAdvanceRequest(count: Int = 2) -> PlaybackRequest {
        var request = PlaybackFixture.request(count: count)
        request.autoAdvance = true
        return request
    }

    // MARK: - Skip (AC5)

    func testSkipBackClampsToZero() async {
        await playAndWaitUntilReady()
        engine.seek(toMs: 10_000)
        engine.skip(bySeconds: -15)
        XCTAssertEqual(engine.state.positionMs, 0)
    }

    func testSkipForwardAddsThirtySecondsAndClampsToTheDuration() async {
        await playAndWaitUntilReady()
        engine.seek(toMs: 10_000)
        engine.skip(bySeconds: 30)
        XCTAssertEqual(engine.state.positionMs, 40_000)
        engine.seek(toMs: 50_000)
        engine.skip(bySeconds: 30)
        XCTAssertEqual(engine.state.positionMs, engine.state.durationMs)
    }

    func testSkipWithoutAnItemDoesNothing() {
        engine.skip(bySeconds: 30)
        XCTAssertNil(engine.state.current)
        XCTAssertEqual(engine.state.positionMs, 0)
    }

    func testLiveDoesNotSkipOrChangeSpeed() async {
        fixture.close()
        fixture = PlaybackFixture(forceLive: true, clock: clock)
        engine.play(PlaybackFixture.request())
        await waitUntil(timeout: 5) { engine.state.isPlaying }
        let before = engine.state.positionMs
        engine.skip(bySeconds: 30)
        XCTAssertLessThan(engine.state.positionMs, before + 5_000, "live does not skip")
        engine.setSpeed(tenths: 15, screen: nil)
        engine.changeRate(2)
        XCTAssertEqual(engine.state.speedTenths, 10)
        XCTAssertTrue(fixture.analytics.events.isEmpty)
    }

    // MARK: - Speed (AC6)

    func testSpeedIsRememberedPerShowAndSurvivesARestart() async throws {
        await playAndWaitUntilReady()
        XCTAssertEqual(engine.state.speedTenths, 10)
        engine.setSpeed(tenths: 15, screen: AudioEngine.analyticsScreen)
        XCTAssertEqual(engine.state.speedTenths, 15)
        XCTAssertEqual(engine.state.rate, 1.5)
        await engine.waitForPersistence()
        let stored = try await fixture.graph.playbackProfiles.speedFor(feedId: 42).int32Value
        XCTAssertEqual(stored, 15)
        let event = try XCTUnwrap(fixture.analytics.events.last)
        XCTAssertEqual(event.name, "speed_change")
        XCTAssertEqual(event.screen, "Player")
        XCTAssertEqual(event.params, ["rate": "1.5", "scope": "show"])

        let files = fixture.files
        fixture.close()
        fixture = PlaybackFixture(files: files, clock: clock)
        engine.play(PlaybackFixture.request())
        await waitUntil(timeout: 5, "speed read back") { engine.state.speedTenths == 15 && engine.state.isPlaying }
    }

    func testSameSpeedIsNotAChange() async {
        await playAndWaitUntilReady()
        engine.setSpeed(tenths: 10, screen: nil)
        XCTAssertTrue(fixture.analytics.events.isEmpty)
    }

    func testRemoteRateIsNormalized() async {
        await playAndWaitUntilReady()
        engine.changeRate(1.04)
        XCTAssertEqual(engine.state.speedTenths, 10)
        engine.changeRate(2.0)
        XCTAssertEqual(engine.state.speedTenths, 20)
        engine.changeRate(9)
        XCTAssertEqual(engine.state.speedTenths, 30)
        XCTAssertEqual(fixture.analytics.events.map { $0.screen }, [nil, nil])
    }

    func testApplyToAllUsesOneSpeedForEveryShow() async throws {
        await playAndWaitUntilReady()
        engine.setSpeed(tenths: 15, screen: nil)
        engine.setSpeedAppliesToAll(true)
        XCTAssertTrue(engine.appliesSpeedToAll)
        await engine.waitForPersistence()
        let profiles = fixture.graph.playbackProfiles
        let global = try await profiles.globalSpeed().int32Value
        let otherShow = try await profiles.speedFor(feedId: 7).int32Value
        XCTAssertEqual(global, 15)
        XCTAssertEqual(otherShow, 15)

        engine.setSpeed(tenths: 20, screen: nil)
        XCTAssertEqual(fixture.analytics.events.last?.params["scope"], "all")

        engine.setSpeedAppliesToAll(false)
        await engine.waitForPersistence()
        XCTAssertEqual(engine.state.speedTenths, 15, "the show's own speed applies again")
    }

    // MARK: - Sleep timer (AC3)

    func testMinutesTimerFadesThenStopsWithoutCountingAsAManualPause() async {
        await playAndWaitUntilReady()
        engine.setSleepTimer(.minutes(5), origin: events.ORIGIN_PLAYER)
        XCTAssertEqual(engine.state.sleepTimer.setting, .minutes(5))
        let deadlineMs = engine.state.sleepTimer.deadline.map { Int64(($0.timeIntervalSince1970 * 1000).rounded()) }
        XCTAssertEqual(deadlineMs, clock.now + 300_000)
        let event = fixture.analytics.events.last
        XCTAssertEqual(event?.name, "sleep_timer_set")
        XCTAssertEqual(event?.params, ["option": "5", "origin": "player"])

        clock.now += 295_000
        try? await Task.sleep(for: .milliseconds(600))
        XCTAssertTrue(engine.state.isPlaying, "still playing while fading")

        clock.now += 5_000
        await waitUntil(timeout: 3, "stopped by the timer") { !engine.state.isPlaying }
        XCTAssertEqual(engine.state.sleepTimer.setting, .off)
        XCTAssertNil(engine.state.sleepTimer.deadline)

        engine.play()
        await waitUntil(timeout: 5, "plays again") { engine.state.isPlaying }
    }

    func testManualPauseCancelsTheTimer() async {
        await playAndWaitUntilReady()
        engine.setSleepTimer(.minutes(15), origin: events.ORIGIN_PLAYER)
        engine.pause()
        XCTAssertEqual(engine.state.sleepTimer.setting, .off)
    }

    func testInterruptionAndLostRouteKeepTheTimer() async {
        await playAndWaitUntilReady()
        engine.setSleepTimer(.minutes(15), origin: events.ORIGIN_PLAYER)
        engine.audioSessionInterruptionBegan()
        XCTAssertEqual(engine.state.sleepTimer.setting, .minutes(15))
        engine.audioSessionInterruptionEnded(shouldResume: true)
        engine.audioSessionOldDeviceUnavailable()
        XCTAssertEqual(engine.state.sleepTimer.setting, .minutes(15))
    }

    func testOffIsLoggedAndClearsTheTimer() {
        engine.setSleepTimer(.endOfEpisode, origin: events.ORIGIN_PLAYER)
        XCTAssertEqual(engine.state.sleepTimer.setting, .endOfEpisode)
        XCTAssertNil(engine.state.sleepTimer.deadline)
        engine.setSleepTimer(.off, origin: events.ORIGIN_PLAYER)
        XCTAssertEqual(engine.state.sleepTimer, SleepTimerStatus())
        XCTAssertEqual(fixture.analytics.events.map { $0.params["option"] }, ["end_of_episode", "off"])
    }

    // MARK: - End of item (AC4)

    func testAutoAdvanceMovesToTheNextItem() async {
        await playAndWaitUntilReady(autoAdvanceRequest())
        engine.seek(toMs: 58_500)
        await waitUntil(timeout: 10, "advanced") { engine.state.index == 1 && engine.state.isPlaying }
        XCTAssertEqual(engine.state.current?.id, "2")
    }

    func testAutoAdvancePausesAtTheLastItem() async {
        var request = autoAdvanceRequest()
        request = PlaybackRequest(items: request.items, startIndex: 1, history: nil, autoAdvance: true)
        await playAndWaitUntilReady(request)
        engine.seek(toMs: 58_500)
        await waitUntil(timeout: 10, "paused at the end") { !engine.state.isPlaying && engine.state.positionMs >= 59_000 }
        XCTAssertEqual(engine.state.index, 1)
    }

    func testEndOfEpisodeTimerStopsEvenWithAutoAdvance() async {
        await playAndWaitUntilReady(autoAdvanceRequest())
        engine.setSleepTimer(.endOfEpisode, origin: events.ORIGIN_PLAYER)
        engine.seek(toMs: 58_500)
        await waitUntil(timeout: 10, "paused at the end") { !engine.state.isPlaying && engine.state.positionMs >= 59_000 }
        try? await Task.sleep(for: .milliseconds(500))
        XCTAssertEqual(engine.state.index, 0, "no advance")
        XCTAssertEqual(engine.state.sleepTimer.setting, .off)
    }

    func testSleepTimerSettingMapsToTheSharedOption() {
        XCTAssertTrue(SleepTimerSetting.off.sharedOption is SleepTimerOptionOff)
        XCTAssertTrue(SleepTimerSetting.endOfEpisode.sharedOption is SleepTimerOptionEndOfEpisode)
        XCTAssertEqual((SleepTimerSetting.minutes(500).sharedOption as? SleepTimerOptionMinutes)?.minutes, 180, "clamped, never a Kotlin require crash")
        XCTAssertEqual((SleepTimerSetting.minutes(0).sharedOption as? SleepTimerOptionMinutes)?.minutes, 1)
        for setting in SleepTimerSetting.sheetOptions {
            XCTAssertEqual(SleepTimerSetting(setting.sharedOption), setting)
        }
    }
}

/// The plain-Swift mirrors of the shared constants stay in sync (M6 §4 item 8).
final class SpeedStepsSharedTests: XCTestCase {
    func testConstantsMatchTheSharedOnes() {
        let speed = PlaybackSpeed.shared
        XCTAssertEqual(SpeedSteps.minTenths, Int(speed.MIN_TENTHS))
        XCTAssertEqual(SpeedSteps.maxTenths, Int(speed.MAX_TENTHS))
        XCTAssertEqual(SpeedSteps.defaultTenths, Int(speed.DEFAULT_TENTHS))
        XCTAssertEqual(SpeedSteps.chips, speed.CHIPS.map(\.intValue))
        XCTAssertEqual(SpeedSteps.remoteRates, speed.REMOTE_RATES.map { $0.doubleValue / 10 })
        for tenths in SpeedSteps.minTenths...SpeedSteps.maxTenths {
            XCTAssertEqual(SpeedSteps.label(tenths), speed.label(tenths: Int32(tenths)))
        }
        let timer = SleepTimer.companion
        XCTAssertEqual(SleepTimerSetting.presetMinutes, timer.PRESET_MINUTES.map(\.intValue))
        XCTAssertEqual(SleepTimerSetting.minMinutes, Int(timer.MIN_MINUTES))
        XCTAssertEqual(SleepTimerSetting.maxMinutes, Int(timer.MAX_MINUTES))
    }
}
