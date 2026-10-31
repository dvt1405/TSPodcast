import AVFoundation
import MediaPlayer
import XCTest
@testable import TSPodcast

/// M3-AC5: the Now Playing dictionary. M6-AC6: the rate follows the speed.
final class NowPlayingInfoTests: XCTestCase {
    private func state(isLive: Bool = false, isPlaying: Bool = true, speedTenths: Int = 10) -> PlaybackState {
        var state = PlaybackState()
        state.current = PlaybackItem(episode: .sample(1), podcast: .sample)
        state.queue = [state.current!, PlaybackItem(episode: .sample(2), podcast: .sample)]
        state.index = 0
        state.isPlaying = isPlaying
        state.isLive = isLive
        state.positionMs = 12_500
        state.durationMs = isLive ? 0 : 1_800_000
        state.speedTenths = speedTenths
        return state
    }

    @MainActor
    func testEpisodeInfo() {
        let info = NowPlayingCenter.info(for: state())
        XCTAssertEqual(info[MPMediaItemPropertyTitle] as? String, "Episode 1")
        XCTAssertEqual(info[MPMediaItemPropertyArtist] as? String, "Podcast 42")
        XCTAssertEqual(info[MPMediaItemPropertyAlbumTitle] as? String, "Podcast 42")
        XCTAssertEqual(info[MPMediaItemPropertyPlaybackDuration] as? Double, 1_800)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? Double, 12.5)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyPlaybackRate] as? Double, 1)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyMediaType] as? UInt, MPNowPlayingInfoMediaType.audio.rawValue)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyIsLiveStream] as? Bool, false)
    }

    @MainActor
    func testLiveHasNoDuration() {
        let info = NowPlayingCenter.info(for: state(isLive: true))
        XCTAssertEqual(info[MPNowPlayingInfoPropertyIsLiveStream] as? Bool, true)
        XCTAssertNil(info[MPMediaItemPropertyPlaybackDuration])
    }

    @MainActor
    func testPausedRateIsZero() {
        XCTAssertEqual(NowPlayingCenter.info(for: state(isPlaying: false))[MPNowPlayingInfoPropertyPlaybackRate] as? Double, 0)
        XCTAssertTrue(NowPlayingCenter.info(for: PlaybackState()).isEmpty)
    }

    @MainActor
    func testRateFollowsTheSpeed() {
        let playing = NowPlayingCenter.info(for: state(speedTenths: 15))
        XCTAssertEqual(playing[MPNowPlayingInfoPropertyPlaybackRate] as? Double, 1.5)
        XCTAssertEqual(playing[MPNowPlayingInfoPropertyDefaultPlaybackRate] as? Double, 1.5)

        let paused = NowPlayingCenter.info(for: state(isPlaying: false, speedTenths: 15))
        XCTAssertEqual(paused[MPNowPlayingInfoPropertyPlaybackRate] as? Double, 0)
        XCTAssertEqual(paused[MPNowPlayingInfoPropertyDefaultPlaybackRate] as? Double, 1.5)

        XCTAssertEqual(NowPlayingCenter.info(for: state())[MPNowPlayingInfoPropertyDefaultPlaybackRate] as? Double, 1.0)
    }
}

/// M3-AC6 / M6-AC5: which remote commands are enabled, and what their handlers do. Next and
/// previous track stay disabled so the Lock Screen shows −15 / +30 (M6 §7 item 5).
@MainActor
final class RemoteCommandsTests: XCTestCase {
    private final class Recorder: RemoteCommandHandling {
        var calls: [String] = []
        func play() { calls.append("play") }
        func pause() { calls.append("pause") }
        func togglePlayPause() { calls.append("toggle") }
        func seek(toMs positionMs: Int64) { calls.append("seek \(positionMs)") }
        func skip(bySeconds seconds: Double) { calls.append("skip \(Int(seconds))") }
        func changeRate(_ rate: Float) { calls.append("rate \(rate)") }
    }

    private let center = MPRemoteCommandCenter.shared()
    private var commands: RemoteCommands!
    private let recorder = Recorder()

    override func setUp() async throws {
        commands = RemoteCommands(center: center)
        commands.install(handler: recorder)
    }

    override func tearDown() async throws {
        commands.uninstall()
    }

    private func state(index: Int, count: Int = 3, isLive: Bool = false) -> PlaybackState {
        var state = PlaybackState()
        state.queue = (1...Int64(count)).map { PlaybackItem(episode: .sample($0), podcast: .sample) }
        state.index = index
        state.current = state.queue[index]
        state.isLive = isLive
        state.durationMs = isLive ? 0 : 60_000
        return state
    }

    private func assertUnsupportedDisabled(file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertFalse(center.nextTrackCommand.isEnabled, file: file, line: line)
        XCTAssertFalse(center.previousTrackCommand.isEnabled, file: file, line: line)
        XCTAssertFalse(center.seekForwardCommand.isEnabled, file: file, line: line)
        XCTAssertFalse(center.seekBackwardCommand.isEnabled, file: file, line: line)
    }

    private func assertSkipAndRate(enabled: Bool, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(center.skipBackwardCommand.isEnabled, enabled, file: file, line: line)
        XCTAssertEqual(center.skipForwardCommand.isEnabled, enabled, file: file, line: line)
        XCTAssertEqual(center.changePlaybackRateCommand.isEnabled, enabled, file: file, line: line)
    }

    func testFirstItem() {
        commands.configure(for: state(index: 0))
        XCTAssertTrue(center.playCommand.isEnabled)
        XCTAssertTrue(center.pauseCommand.isEnabled)
        XCTAssertTrue(center.togglePlayPauseCommand.isEnabled)
        XCTAssertTrue(center.changePlaybackPositionCommand.isEnabled)
        assertSkipAndRate(enabled: true)
        assertUnsupportedDisabled()
    }

    func testMiddleAndLastItemStillHaveNoTrackCommands() {
        commands.configure(for: state(index: 1))
        assertUnsupportedDisabled()
        commands.configure(for: state(index: 2))
        assertSkipAndRate(enabled: true)
        assertUnsupportedDisabled()
    }

    func testSkipIntervalsAndRates() {
        XCTAssertEqual(center.skipBackwardCommand.preferredIntervals, [15])
        XCTAssertEqual(center.skipForwardCommand.preferredIntervals, [30])
        XCTAssertEqual(center.changePlaybackRateCommand.supportedPlaybackRates, [0.5, 0.8, 1.0, 1.2, 1.5, 2.0, 2.5, 3.0])
    }

    func testLiveHasNoPositionSkipOrRate() {
        commands.configure(for: state(index: 1, isLive: true))
        XCTAssertFalse(center.changePlaybackPositionCommand.isEnabled)
        XCTAssertTrue(center.togglePlayPauseCommand.isEnabled)
        assertSkipAndRate(enabled: false)
        assertUnsupportedDisabled()
    }

    func testNothingLoaded() {
        commands.configure(for: PlaybackState())
        XCTAssertFalse(center.playCommand.isEnabled)
        XCTAssertFalse(center.changePlaybackPositionCommand.isEnabled)
        assertSkipAndRate(enabled: false)
        assertUnsupportedDisabled()
    }

    func testHandlersDriveTheEngine() {
        XCTAssertEqual(commands.handlePlay(), .success)
        XCTAssertEqual(commands.handlePause(), .success)
        XCTAssertEqual(commands.handleTogglePlayPause(), .success)
        XCTAssertEqual(commands.handleChangePosition(seconds: 42.5), .success)
        XCTAssertEqual(commands.handleChangePosition(seconds: .nan), .commandFailed)
        XCTAssertEqual(commands.handleSkip(seconds: -15), .success)
        XCTAssertEqual(commands.handleSkip(seconds: 30), .success)
        XCTAssertEqual(commands.handleSkip(seconds: .infinity), .commandFailed)
        XCTAssertEqual(commands.handleChangeRate(1.5), .success)
        XCTAssertEqual(commands.handleChangeRate(.nan), .commandFailed)
        XCTAssertEqual(commands.handleChangeRate(0), .commandFailed)
        XCTAssertEqual(recorder.calls, ["play", "pause", "toggle", "seek 42500", "skip -15", "skip 30", "rate 1.5"])
    }

    func testHandlersWithoutEngineHaveNoItem() {
        commands.uninstall()
        let orphan = RemoteCommands(center: center)
        XCTAssertEqual(orphan.handleSkip(seconds: 30), .noActionableNowPlayingItem)
        XCTAssertEqual(orphan.handleChangeRate(1.5), .noActionableNowPlayingItem)
    }
}

/// M3-AC3: how the published state is derived.
final class PlaybackStateTests: XCTestCase {
    func testBuffering() {
        XCTAssertTrue(PlaybackState.isBuffering(timeControlStatus: .waitingToPlayAtSpecifiedRate, itemStatus: .readyToPlay, wantsToPlay: true, failed: false))
        XCTAssertTrue(PlaybackState.isBuffering(timeControlStatus: .paused, itemStatus: .unknown, wantsToPlay: true, failed: false))
        XCTAssertTrue(PlaybackState.isBuffering(timeControlStatus: .paused, itemStatus: nil, wantsToPlay: true, failed: false))
        XCTAssertFalse(PlaybackState.isBuffering(timeControlStatus: .playing, itemStatus: .readyToPlay, wantsToPlay: true, failed: false))
        XCTAssertFalse(PlaybackState.isBuffering(timeControlStatus: .paused, itemStatus: .unknown, wantsToPlay: false, failed: false))
        XCTAssertFalse(PlaybackState.isBuffering(timeControlStatus: .waitingToPlayAtSpecifiedRate, itemStatus: .failed, wantsToPlay: true, failed: true))
    }

    func testMilliseconds() {
        XCTAssertEqual(PlaybackState.milliseconds(CMTime(value: 1_500, timescale: 1_000)), 1_500)
        XCTAssertEqual(PlaybackState.milliseconds(.indefinite), 0)
        XCTAssertEqual(PlaybackState.milliseconds(.invalid), 0)
        XCTAssertEqual(PlaybackState.milliseconds(.positiveInfinity), 0)
        XCTAssertEqual(PlaybackState.milliseconds(CMTime(seconds: .nan, preferredTimescale: 600)), 0)
        XCTAssertTrue(PlaybackState.isIndefinite(.indefinite))
        XCTAssertFalse(PlaybackState.isIndefinite(CMTime(value: 60, timescale: 1)))
    }

    func testProgressAndEnds() {
        var state = PlaybackState()
        XCTAssertFalse(state.hasNext)
        XCTAssertFalse(state.hasPrevious)
        state.queue = [.init(episode: .sample(1), podcast: .sample), .init(episode: .sample(2), podcast: .sample)]
        state.current = state.queue[0]
        XCTAssertTrue(state.hasNext)
        XCTAssertFalse(state.hasPrevious)
        state.positionMs = 15_000
        state.durationMs = 60_000
        XCTAssertEqual(state.progress, 0.25, accuracy: 0.0001)
        state.durationMs = 0
        XCTAssertEqual(state.progress, 0)
    }

    @MainActor
    func testProbeLabel() {
        XCTAssertEqual(PlaybackProbe.label(PlaybackState()), "")
        var state = PlaybackState()
        state.queue = [.init(episode: .sample(1), podcast: .sample), .init(episode: .sample(2), podcast: .sample)]
        state.current = state.queue[1]
        state.index = 1
        state.isPlaying = true
        state.positionMs = 1_000
        state.durationMs = 60_000
        XCTAssertEqual(
            PlaybackProbe.label(state),
            "id=2;feedId=42;index=1;count=2;playing=1;buffering=0;positionMs=1000;durationMs=60000;live=0;fav=0;error=0"
        )
    }
}

/// What a Detail or Live tap hands to the player.
final class PlaybackItemTests: XCTestCase {
    func testFromEpisode() {
        let item = PlaybackItem(episode: .sample(7), podcast: .sample)
        XCTAssertEqual(item.id, "7")
        XCTAssertEqual(item.feedId, 42)
        XCTAssertEqual(item.url, "https://media.example.com/e7.mp3")
        XCTAssertEqual(item.title, "Episode 7")
        XCTAssertEqual(item.podcastTitle, "Podcast 42")
        XCTAssertEqual(item.artworkURL, "https://img.example.com/e7.jpg")
        XCTAssertEqual(item.referer, "https://site.example.com/e7")
        XCTAssertEqual(item.durationMs, 1_800_000)
        XCTAssertFalse(item.isLive)
        XCTAssertEqual(item.summary, "Summary 7", "stored with a favourite (M4)")
        // The shared item carries everything but the description, which only the favourite uses.
        var roundTrip = PlaybackItem(item.shared())
        XCTAssertEqual(roundTrip.summary, "")
        roundTrip.summary = item.summary
        XCTAssertEqual(roundTrip, item)
        XCTAssertTrue(item.shared(isLive: true).isLive)
    }

    func testEpisodeWithoutImageUsesThePodcastCover() {
        let row = EpisodeRow(id: 1, title: "T", summary: "", imageURL: nil, referer: nil, publishedAt: nil, durationSeconds: 0, enclosureURL: "u", enclosureType: "")
        let podcast = PodcastRoute(feedId: 1, title: "P", summary: "", imageURL: "https://img.example.com/p.jpg")
        XCTAssertEqual(PlaybackItem(episode: row, podcast: podcast).artworkURL, "https://img.example.com/p.jpg")
    }

    func testFromLiveUsesFeedTitle() {
        let live = LiveRowItem.sample(3)
        let item = PlaybackItem(live: live)
        XCTAssertEqual(item.id, "3")
        XCTAssertEqual(item.feedId, 903)
        XCTAssertEqual(item.podcastTitle, "Feed 3")
        XCTAssertEqual(item.url, "https://media.example.com/live3.mp3")
        XCTAssertFalse(item.isLive, "live only when the stream has no duration")

        var untitled = live
        untitled.feedTitle = nil
        XCTAssertEqual(PlaybackItem(live: untitled).podcastTitle, "Live 3")
    }

    func testLiveRequestKeepsTheWholeList() {
        let request = PlaybackRequest(live: (1...7).map { LiveRowItem.sample(Int64($0)) }, startIndex: 4)
        XCTAssertEqual(request.items.count, 7)
        XCTAssertEqual(request.startItem?.id, "5")
        XCTAssertNil(PlaybackRequest(live: [], startIndex: 0).startItem)
    }
}
