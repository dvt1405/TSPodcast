import AVFoundation
import XCTest
@testable import TSPodcast

/// M3-AC4: interruption, route change and media services reset, with synthetic notifications.
@MainActor
final class AudioSessionControllerTests: XCTestCase {
    private var fixture: PlaybackFixture!

    override func setUp() async throws {
        fixture = PlaybackFixture()
        fixture.engine.play(PlaybackFixture.request())
        await waitUntil(timeout: 5) { self.fixture.engine.state.isPlaying }
    }

    override func tearDown() async throws {
        fixture.close()
        fixture = nil
    }

    private var engine: AudioEngine { fixture.engine }
    private var session: AudioSessionController { fixture.session }

    private func interruption(_ type: AVAudioSession.InterruptionType, resume: Bool = false, reason: UInt? = nil) -> AudioSessionController.Interruption {
        var info: [AnyHashable: Any] = [AVAudioSessionInterruptionTypeKey: type.rawValue]
        if resume { info[AVAudioSessionInterruptionOptionKey] = AVAudioSession.InterruptionOptions.shouldResume.rawValue }
        if let reason { info[AVAudioSessionInterruptionReasonKey] = reason }
        return AudioSessionController.interruption(Notification(name: AVAudioSession.interruptionNotification, object: nil, userInfo: info))
    }

    func testInterruptionPausesThenResumes() async {
        session.handleInterruption(interruption(.began))
        await waitUntil(timeout: 3, "paused") { !self.engine.state.isPlaying }
        session.handleInterruption(interruption(.ended, resume: true))
        await waitUntil(timeout: 3, "resumed") { self.engine.state.isPlaying }
    }

    func testNoResumeWithoutTheHint() async {
        session.handleInterruption(interruption(.began))
        await waitUntil(timeout: 3) { !self.engine.state.isPlaying }
        session.handleInterruption(interruption(.ended, resume: false))
        try? await Task.sleep(for: .milliseconds(500))
        XCTAssertFalse(engine.state.isPlaying)
    }

    func testNoResumeWhenTheUserHadPaused() async {
        engine.pause()
        await waitUntil(timeout: 3) { !self.engine.state.isPlaying }
        session.handleInterruption(interruption(.began))
        session.handleInterruption(interruption(.ended, resume: true))
        try? await Task.sleep(for: .milliseconds(500))
        XCTAssertFalse(engine.state.isPlaying, "a user pause wins over shouldResume")
    }

    func testSuspendedAppIsNotAnInterruption() async {
        let parsed = interruption(.began, reason: 1)
        XCTAssertTrue(parsed.wasSuspended)
        session.handleInterruption(parsed)
        try? await Task.sleep(for: .milliseconds(300))
        XCTAssertTrue(engine.state.isPlaying)
    }

    func testOldDeviceUnavailablePauses() async {
        session.handleRouteChange(.newDeviceAvailable)
        try? await Task.sleep(for: .milliseconds(300))
        XCTAssertTrue(engine.state.isPlaying, "a new route does not pause")
        let note = Notification(name: AVAudioSession.routeChangeNotification, object: nil, userInfo: [
            AVAudioSessionRouteChangeReasonKey: AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue,
        ])
        session.handleRouteChange(AudioSessionController.routeChangeReason(note))
        await waitUntil(timeout: 3, "paused") { !self.engine.state.isPlaying }
    }

    func testMediaServicesResetRebuildsThePlayer() async {
        engine.seek(toMs: 12_000)
        await waitUntil(timeout: 5) { self.engine.state.positionMs >= 12_000 }
        let oldPlayer = engine.playerIdentity
        let item = engine.state.current
        let position = engine.state.positionMs

        session.handleMediaServicesReset()

        XCTAssertNotEqual(engine.playerIdentity, oldPlayer, "a new AVPlayer")
        XCTAssertEqual(engine.state.current, item)
        await waitUntil(timeout: 5, "reloaded at the old position") {
            self.engine.state.durationMs > 0 && abs(self.engine.state.positionMs - position) < 1_500
        }
        try? await Task.sleep(for: .milliseconds(500))
        XCTAssertFalse(engine.state.isPlaying, "no autoplay after a reset")
    }

    func testCategoryIsSpokenAudioPlayback() {
        let shared = AVAudioSession.sharedInstance()
        XCTAssertEqual(shared.category, .playback)
        XCTAssertEqual(shared.mode, .spokenAudio)
        XCTAssertEqual(shared.routeSharingPolicy, .longFormAudio)
    }
}
