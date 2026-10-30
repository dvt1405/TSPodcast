import Foundation
import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// A `SharedGraph` on a fresh DataStore file and a fresh Room database file, and an engine
/// playing the 60 s fixture tone.
@MainActor
struct PlaybackFixture {
    let graph: SharedGraph
    let positions: PlaybackPositions
    let history: PlaybackHistory
    let favourites: FavouriteRepository
    let session = AudioSessionController()
    let engine: AudioEngine

    init(audioURL: URL? = PlaybackFixture.toneURL, forceLive: Bool = false) {
        let directory = FileManager.default.temporaryDirectory
        let name = "playback-\(UUID().uuidString)"
        graph = SharedGraph(
            credentials: BundleCredentialsProvider(),
            config: NoOpConfig(),
            preferencesPath: directory.appending(path: "\(name).preferences_pb").path(percentEncoded: false),
            databasePath: directory.appending(path: "\(name).db").path(percentEncoded: false)
        )
        positions = graph.playbackPositions
        history = graph.playbackHistory
        favourites = graph.favouriteRepository
        engine = AudioEngine(
            positions: positions,
            history: history,
            favourites: favourites,
            session: session,
            hooks: AudioEngine.Hooks(audioURLOverride: audioURL, forceLive: forceLive)
        )
    }

    static var toneURL: URL {
        Bundle(for: BundleToken.self).url(forResource: "tone-60s", withExtension: "m4a")!
    }

    /// Queue of `count` episodes of podcast 42 (ids 1…count).
    static func request(count: Int = 3, startIndex: Int = 0) -> PlaybackRequest {
        PlaybackRequest(podcast: .sample, episodes: (1...Int64(count)).map(EpisodeRow.sample), startIndex: startIndex)
    }

    func close() {
        engine.shutdown()
        graph.close()
    }
}

private final class BundleToken {}

extension XCTestCase {
    /// Polls `condition` on the main actor every 50 ms.
    @MainActor
    func waitUntil(timeout: TimeInterval = 10, _ message: @autoclosure () -> String = "", file: StaticString = #filePath, line: UInt = #line, _ condition: () -> Bool) async {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() {
            if Date() >= deadline {
                XCTFail("timed out: \(message())", file: file, line: line)
                return
            }
            try? await Task.sleep(for: .milliseconds(50))
        }
    }
}
