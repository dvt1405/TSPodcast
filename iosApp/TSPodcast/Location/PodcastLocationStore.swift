import Foundation
@preconcurrency import Shared

/// Where the podcast locations are kept: shared `PodcastLocationRepository` in the app, a fake in
/// unit tests. Both calls return the saved list in priority order, never empty.
protocol PodcastLocationStore: Sendable {
    /// The saved locations; on first launch the device location (region, else language, else US).
    func load() async -> [PodcastLocationItem]
    /// Saves `regions` (normalised by shared: known, deduplicated, at most 3) and returns what is saved.
    func save(regions: [String]) async -> [PodcastLocationItem]
}

/// `PodcastLocationStore` over shared `PodcastLocationRepository` (DataStore key `PodcastLocations`,
/// the Android value `VN,US`).
///
/// `@unchecked Sendable`: an immutable reference to a Kotlin object, which any thread may call.
struct SharedPodcastLocationStore: PodcastLocationStore, @unchecked Sendable {
    let repository: PodcastLocationRepository

    func load() async -> [PodcastLocationItem] {
        do {
            return try await repository.current().map(PodcastLocationItem.init)
        } catch {
            // Only cancellation reaches here (the store never throws).
            AppLog.app.error("podcast_locations_load_failed type=\(String(describing: type(of: error)), privacy: .public)")
            return [PodcastLocationItem(PodcastLocations.shared.DEFAULT)]
        }
    }

    func save(regions: [String]) async -> [PodcastLocationItem] {
        do {
            return try await repository.setLocations(regions: regions).map(PodcastLocationItem.init)
        } catch {
            AppLog.app.error("podcast_locations_save_failed type=\(String(describing: type(of: error)), privacy: .public)")
            return await load()
        }
    }
}
