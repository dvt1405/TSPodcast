import Foundation
@preconcurrency import Shared

/// Where the app's Room database lives (M4 §3.1, architecture §3.3): one file in Application
/// Support, excluded from iCloud/iTunes backup because it is an API cache under the PodcastIndex
/// terms (plus favourites, which the API can rebuild).
enum DatabaseLocation {
    /// `Application Support/TSPodcastDatabase.db`.
    static var url: URL {
        URL.applicationSupportDirectory.appending(path: PodcastDatabase.companion.FILE_NAME)
    }

    /// SQLite writes these next to the database (WAL mode, rollback journal).
    static let sideFileSuffixes = ["-wal", "-shm", "-journal"]

    /// Marks the database file and its side files that exist as excluded from backup. Returns how
    /// many files were marked. Run after the database has been opened (the file exists then).
    @discardableResult
    static func excludeFromBackup(_ url: URL) -> Int {
        let files = [url] + sideFileSuffixes.map { URL(filePath: url.path(percentEncoded: false) + $0) }
        var marked = 0
        for var file in files where FileManager.default.fileExists(atPath: file.path(percentEncoded: false)) {
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            do {
                try file.setResourceValues(values)
                marked += 1
            } catch {
                AppLog.app.error("db_backup_exclusion_failed type=\(String(describing: type(of: error)), privacy: .public)")
            }
        }
        return marked
    }
}

/// The launch-time cache cleanup (features-spec §1 "PruneApiCache mỗi lần launch"). `AppEnvironment`
/// runs it once per process, off the main actor; unit tests use a fake.
protocol LaunchCachePruning: Sendable {
    func prune() async
}

/// Shared `PruneApiCache` (7 days, keeps favourites), then the backup exclusion of the file it
/// just opened. `PruneApiCache` never throws on a database error (it reports to `Crash`); a
/// cancellation is only logged.
struct SharedCachePruner: LaunchCachePruning, @unchecked Sendable {
    let pruneApiCache: PruneApiCache
    let databaseURL: URL

    func prune() async {
        do {
            let result = try await pruneApiCache.invoke()
            AppLog.app.notice(
                "prune_api_cache podcasts=\(result.podcasts, privacy: .public) episodes=\(result.episodes, privacy: .public) feeds=\(result.feeds, privacy: .public)"
            )
        } catch {
            AppLog.app.error("prune_api_cache_failed type=\(String(describing: type(of: error)), privacy: .public)")
        }
        let marked = DatabaseLocation.excludeFromBackup(databaseURL)
        AppLog.app.notice("db_backup_excluded files=\(marked, privacy: .public)")
    }
}
