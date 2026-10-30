import AVFoundation
import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M3-AC3: one AVPlayer, the queue, pause at the end of an episode, state, history, positions.
/// Plays the 60 s fixture tone for every item.
@MainActor
final class AudioEngineTests: XCTestCase {
    private var fixture: PlaybackFixture!

    override func setUp() async throws {
        fixture = PlaybackFixture()
    }

    override func tearDown() async throws {
        fixture.close()
        fixture = nil
    }

    private var engine: AudioEngine { fixture.engine }

    func testPlayPlaysAndPositionAdvances() async {
        engine.play(PlaybackFixture.request(startIndex: 1))
        XCTAssertEqual(engine.state.current?.id, "2")
        XCTAssertEqual(engine.state.index, 1)
        XCTAssertEqual(engine.state.queue.count, 3)
        await waitUntil(timeout: 5, "playing") { engine.state.isPlaying }
        let first = engine.state.positionMs
        await waitUntil(timeout: 5, "position advances") { engine.state.positionMs >= first + 1_000 }
        XCTAssertEqual(engine.state.durationMs / 1000, 60)
        XCTAssertFalse(engine.state.isLive)
        XCTAssertNil(engine.state.error)
    }

    func testPauseAndToggle() async {
        engine.play(PlaybackFixture.request())
        await waitUntil(timeout: 5) { engine.state.isPlaying }
        engine.pause()
        await waitUntil(timeout: 3, "paused") { !engine.state.isPlaying }
        engine.togglePlayPause()
        await waitUntil(timeout: 3, "playing again") { engine.state.isPlaying }
        engine.togglePlayPause()
        await waitUntil(timeout: 3, "paused again") { !engine.state.isPlaying }
    }

    func testPreviousAndNextStopAtTheEnds() async {
        engine.play(PlaybackFixture.request(startIndex: 0))
        XCTAssertFalse(engine.state.hasPrevious)
        engine.skipPrevious()
        XCTAssertEqual(engine.state.index, 0)
        engine.skipNext()
        XCTAssertEqual(engine.state.index, 1)
        XCTAssertEqual(engine.state.current?.id, "2")
        engine.select(index: 2)
        XCTAssertEqual(engine.state.index, 2)
        XCTAssertFalse(engine.state.hasNext)
        engine.skipNext()
        XCTAssertEqual(engine.state.index, 2)
        engine.select(index: 9)
        XCTAssertEqual(engine.state.index, 2)
        engine.skipPrevious()
        XCTAssertEqual(engine.state.index, 1)
    }

    /// `setPauseAtEndOfMediaItems(true)`: the end pauses and the index stays.
    func testEndOfEpisodePausesWithoutAdvancing() async {
        engine.play(PlaybackFixture.request(startIndex: 0))
        await waitUntil(timeout: 5) { engine.state.isPlaying && engine.state.durationMs > 0 }
        engine.seek(toMs: 58_500)
        await waitUntil(timeout: 10, "paused at the end") { !engine.state.isPlaying && engine.state.positionMs >= 59_000 }
        try? await Task.sleep(for: .milliseconds(500))
        XCTAssertEqual(engine.state.index, 0, "no auto-advance")
        XCTAssertEqual(engine.state.current?.id, "1")
        XCTAssertFalse(engine.state.isPlaying)
        XCTAssertNil(engine.state.error)

        engine.play()
        await waitUntil(timeout: 5, "play again restarts the item") { engine.state.isPlaying && engine.state.positionMs < 10_000 }
    }

    func testCurrentItemIdIsSavedOnEveryChange() async throws {
        engine.play(PlaybackFixture.request(startIndex: 1))
        await engine.waitForPersistence()
        var current = try await fixture.history.currentItemId()
        XCTAssertEqual(current, "2")
        let stored = try await fixture.store.getString(key: "extra:current_item_id")
        XCTAssertEqual(stored, "2")

        engine.skipNext()
        await engine.waitForPersistence()
        current = try await fixture.history.currentItemId()
        XCTAssertEqual(current, "3")

        engine.skipPrevious()
        engine.skipPrevious()
        await engine.waitForPersistence()
        current = try await fixture.history.currentItemId()
        XCTAssertEqual(current, "1")
        let recent = try await fixture.history.recentItems().map(\.id)
        XCTAssertEqual(recent, ["1", "2", "3"])
    }

    /// M3 §4.3: the position saved when the episode changes is used to resume it.
    func testResumesTheSavedPosition() async throws {
        engine.play(PlaybackFixture.request(startIndex: 0))
        await waitUntil(timeout: 5) { engine.state.isPlaying && engine.state.durationMs > 0 }
        engine.seek(toMs: 20_000)
        await waitUntil(timeout: 5, "seeked") { engine.state.positionMs >= 20_000 }

        engine.select(index: 1)
        await engine.waitForPersistence()
        let first = PlaybackItem(episode: .sample(1), podcast: .sample).shared()
        let saved = try await fixture.positions.resumePositionMs(item: first).int64Value
        XCTAssertGreaterThanOrEqual(saved, 20_000)
        XCTAssertLessThan(saved, 25_000)

        engine.select(index: 0)
        await waitUntil(timeout: 5, "resumed") { engine.state.isPlaying && engine.state.positionMs >= 19_500 }
        XCTAssertLessThan(engine.state.positionMs, 30_000)
    }

    /// M3 review round 1: a slider seek made while the resume seek is still in flight wins;
    /// before the fix the resume seek's completion reset the target and playback started at 20 s.
    func testSeekDuringTheResumeSeekIsKept() async throws {
        let first = PlaybackItem(episode: .sample(1), podcast: .sample).shared()
        try await fixture.positions.record(item: first, positionMs: 20_000, durationMs: 60_000)
        var sent = 0
        engine.onStartSeekSent = { [unowned engine] in
            sent += 1
            if sent == 1 { engine.seek(toMs: 40_000) }
        }

        engine.play(PlaybackFixture.request(startIndex: 0))
        await waitUntil(timeout: 5, "playing") { engine.state.isPlaying && engine.state.positionMs > 0 }
        XCTAssertEqual(sent, 2, "the new target is applied with a second seek")
        XCTAssertGreaterThanOrEqual(engine.state.positionMs, 39_500)
        XCTAssertLessThan(engine.state.positionMs, 45_000)
    }

    func testSeekToStartDuringTheResumeSeekIsKept() async throws {
        let first = PlaybackItem(episode: .sample(1), podcast: .sample).shared()
        try await fixture.positions.record(item: first, positionMs: 20_000, durationMs: 60_000)
        engine.onStartSeekSent = { [unowned engine] in engine.seek(toMs: 0) }

        engine.play(PlaybackFixture.request(startIndex: 0))
        await waitUntil(timeout: 5, "playing") { engine.state.isPlaying }
        await waitUntil(timeout: 3, "position published") { engine.state.positionMs > 0 }
        XCTAssertLessThan(engine.state.positionMs, 5_000)
    }

    func testFavouriteFollowsTheItem() async {
        engine.play(PlaybackFixture.request(startIndex: 0))
        XCTAssertFalse(engine.state.isFavourite)
        engine.toggleFavourite()
        XCTAssertTrue(engine.state.isFavourite)
        XCTAssertTrue(fixture.favourites.isFavourite(type: .podcastepisode, id: "1"))
        engine.skipNext()
        XCTAssertFalse(engine.state.isFavourite)
        engine.skipPrevious()
        XCTAssertTrue(engine.state.isFavourite)
    }

    func testForcedLiveHasNoDurationAndNoSeek() async {
        fixture.close()
        fixture = PlaybackFixture(forceLive: true)
        engine.play(PlaybackFixture.request())
        await waitUntil(timeout: 5) { engine.state.isPlaying }
        XCTAssertTrue(engine.state.isLive)
        XCTAssertEqual(engine.state.durationMs, 0)
        XCTAssertEqual(engine.state.progress, 0)
        let before = engine.state.positionMs
        engine.seek(toMs: 40_000)
        XCTAssertLessThan(engine.state.positionMs, before + 5_000, "live does not seek")
    }
}

/// M3-AC13: an item that cannot load reports the error; retry keeps trying the same item.
@MainActor
final class AudioEngineErrorTests: XCTestCase {
    func testMissingFileFails() async {
        let fixture = PlaybackFixture(audioURL: URL(fileURLWithPath: "/nonexistent/tsp-missing.m4a"))
        defer { fixture.close() }
        let engine = fixture.engine
        engine.play(PlaybackFixture.request(startIndex: 1))
        await waitUntil(timeout: 10, "error") { engine.state.error == .failed }
        XCTAssertFalse(engine.state.isPlaying)
        XCTAssertFalse(engine.state.isBuffering)
        engine.retry()
        XCTAssertNil(engine.state.error, "retry clears the error while loading")
        XCTAssertEqual(engine.state.index, 1)
        await waitUntil(timeout: 10, "error again") { engine.state.error == .failed }
    }

    func testInvalidURLFails() async {
        let fixture = PlaybackFixture(audioURL: nil)
        defer { fixture.close() }
        let item = PlaybackItem(id: "9", feedId: 1, url: "not a url", title: "T", podcastTitle: "P", artworkURL: nil, referer: nil, durationMs: 0, isLive: false)
        fixture.engine.play(PlaybackRequest(items: [item], startIndex: 0))
        await waitUntil(timeout: 5, "error") { fixture.engine.state.error == .failed }
    }
}
