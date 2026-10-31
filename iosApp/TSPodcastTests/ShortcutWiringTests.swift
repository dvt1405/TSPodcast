import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M6-AC19/AC20: the app side of the shortcuts — the event names come from shared
/// `AnalyticsEvents`, "Tiếp tục nghe" resumes the saved episode through `PlaybackHistory` +
/// `FavouriteResolver` (a real graph on temporary files), and the engine is the player.
@MainActor
final class ShortcutWiringTests: XCTestCase {
    func testEventNamesAreTheSharedOnes() {
        let events = AnalyticsEvents.shared
        XCTAssertEqual(
            ShortcutEvents.app,
            ShortcutEvents(invoked: events.SHORTCUT_INVOKED, intentParameter: events.PARAM_INTENT, sleepTimerOrigin: events.ORIGIN_SHORTCUT)
        )
        XCTAssertEqual(ShortcutEvents.app.invoked, "shortcut_invoked")
        XCTAssertEqual(ShortcutEvents.app.sleepTimerOrigin, "shortcut")
    }

    func testNothingSavedResolvesToNil() async {
        let fixture = SharedGraphFixture()
        defer { fixture.close() }
        let history = SharedShortcutHistory(history: fixture.graph.playbackHistory, resolver: fixture.graph.favouriteResolver)

        let request = await history.lastPlayed()

        XCTAssertNil(request)
    }

    func testTheSavedEpisodeStartsInItsSavedList() async throws {
        let fixture = SharedGraphFixture()
        defer { fixture.close() }
        let podcast = SharedModelFactory.podcast(42, title: "Podcast 42")
        let episodes = [3, 2, 1].map { SharedModelFactory.episode(Int64($0), feedId: 42) }
        try await fixture.graph.playbackHistory.savePlaying(episode: episodes[1], podcast: podcast, playlist: episodes)
        let history = SharedShortcutHistory(history: fixture.graph.playbackHistory, resolver: fixture.graph.favouriteResolver)

        let lastPlayed = await history.lastPlayed()
        let request = try XCTUnwrap(lastPlayed)

        XCTAssertEqual(request.items.map(\.id), ["3", "2", "1"], "the saved list, newest first")
        XCTAssertEqual(request.startItem?.id, "2")
        XCTAssertFalse(request.autoAdvance)
        XCTAssertNotNil(request.history)
    }

    func testAnUnknownSavedIdResolvesToNil() async throws {
        let fixture = SharedGraphFixture()
        defer { fixture.close() }
        try await fixture.graph.playbackHistory.saveCurrentId(itemId: "999")
        let history = SharedShortcutHistory(history: fixture.graph.playbackHistory, resolver: fixture.graph.favouriteResolver)

        let request = await history.lastPlayed()

        XCTAssertNil(request)
    }

    func testTheEngineHasNoItemBeforeAPlay() async {
        let fixture = PlaybackFixture()
        defer { fixture.close() }

        XCTAssertFalse(fixture.engine.hasCurrentItem)
        fixture.engine.start(PlaybackFixture.request(count: 2, startIndex: 1))
        XCTAssertTrue(fixture.engine.hasCurrentItem)
        await fixture.engine.waitForPersistence()
    }
}
