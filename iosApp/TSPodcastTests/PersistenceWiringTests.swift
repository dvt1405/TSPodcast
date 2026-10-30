import Darwin
import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// M4-AC12: one database in Application Support, excluded from backup, and `PruneApiCache` once
/// per launch off the main actor (features-spec §1, §2 Cache; architecture §3.3).
@MainActor
final class PersistenceWiringTests: XCTestCase {
    func testDatabaseLivesInApplicationSupport() {
        let url = DatabaseLocation.url
        XCTAssertEqual(url.lastPathComponent, "TSPodcastDatabase.db")
        XCTAssertEqual(url.deletingLastPathComponent().standardizedFileURL, URL.applicationSupportDirectory.standardizedFileURL)
    }

    /// The real pruner opens the database (Room creates the file on the first query) and marks it
    /// excluded from backup.
    func testPrunerOpensTheDatabaseAndExcludesItFromBackup() async throws {
        let fixture = SharedGraphFixture()
        defer { fixture.close() }
        let url = fixture.files.database
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path(percentEncoded: false)), "nothing before the first query")

        await SharedCachePruner(pruneApiCache: fixture.graph.pruneApiCache, databaseURL: url).prune()

        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path(percentEncoded: false)))
        let values = try url.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
        for suffix in DatabaseLocation.sideFileSuffixes {
            let side = URL(filePath: url.path(percentEncoded: false) + suffix)
            guard FileManager.default.fileExists(atPath: side.path(percentEncoded: false)) else { continue }
            XCTAssertEqual(try side.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true, suffix)
        }
    }

    func testAppEnvironmentPrunesOnceOffTheMainActor() async throws {
        let fixture = SharedGraphFixture()
        let pruner = RecordingPruner()
        let environment = AppEnvironment(launchOptions: .none, artworkLoader: .shared, graph: fixture.graph, pruner: pruner)
        defer {
            environment.audioEngine.shutdown()
            fixture.close()
        }

        await waitUntil("prune ran") { pruner.calls.count == 1 }
        try await Task.sleep(for: .milliseconds(300))

        XCTAssertEqual(pruner.calls.count, 1, "once per launch")
        XCTAssertEqual(pruner.calls.first?.onMainThread, false, "never on the main thread")
    }

    func testMissingDatabaseFileIsNotAnError() {
        let url = FileManager.default.temporaryDirectory.appending(path: "missing-\(UUID().uuidString).db")
        XCTAssertEqual(DatabaseLocation.excludeFromBackup(url), 0)
    }
}

/// Records each `prune()` and whether it ran on the main thread.
private final class RecordingPruner: LaunchCachePruning, @unchecked Sendable {
    struct Call {
        let onMainThread: Bool
    }

    private let lock = NSLock()
    private var _calls: [Call] = []

    var calls: [Call] { lock.withLock { _calls } }

    func prune() async {
        let onMain = pthread_main_np() != 0
        lock.withLock { _calls.append(Call(onMainThread: onMain)) }
    }
}
