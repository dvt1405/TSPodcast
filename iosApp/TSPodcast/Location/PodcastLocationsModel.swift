import Foundation
import Observation
@preconcurrency import Shared

/// What a change of the locations reloads on Home (shared `LocationChange`, Android `MainViewModel`).
enum PodcastLocationChange: Equatable, Sendable {
    case none
    /// Another primary location: every Home list (they all use its language).
    case primary
    /// Reordered, added or removed secondaries: only the "by location" lists.
    case secondary

    static func between(_ previous: [PodcastLocationItem], _ current: [PodcastLocationItem]) -> PodcastLocationChange {
        if previous == current { return .none }
        return previous.first == current.first ? .secondary : .primary
    }
}

/// "Vị trí podcast" (Android `PodcastLocationPrefs` + `PodcastLocationSheet`): up to 3 locations in
/// priority order, the first one primary. One per process (`AppEnvironment`), shared by Settings,
/// the Home sheet, Home and the Search topic page.
///
/// Every edit is saved at once (no "Save" step, like Android). Each change logs
/// `podcast_locations_change` and tells the listeners (`onChange`), which reload Home.
@MainActor
@Observable
final class PodcastLocationsModel {
    /// The locations in priority order; empty until `load()` finished.
    private(set) var locations: [PodcastLocationItem] = []
    /// Every supported location, Vietnam first.
    let supported: [PodcastLocationItem]
    let maxLocations: Int

    @ObservationIgnored private let store: any PodcastLocationStore
    @ObservationIgnored private let analytics: any AnalyticsTracker
    @ObservationIgnored private var listeners: [(_ previous: [PodcastLocationItem], _ current: [PodcastLocationItem]) -> Void] = []
    @ObservationIgnored private var loadTask: Task<Void, Never>?

    init(
        store: any PodcastLocationStore,
        analytics: any AnalyticsTracker,
        supported: [PodcastLocationItem] = PodcastLocationItem.supported,
        maxLocations: Int = PodcastLocationItem.maxLocations
    ) {
        self.store = store
        self.analytics = analytics
        self.supported = supported
        self.maxLocations = maxLocations
    }

    var primary: PodcastLocationItem? { locations.first }

    /// Supported locations not chosen yet, in the supported order.
    var others: [PodcastLocationItem] {
        supported.filter { !locations.contains($0) }
    }

    var isFull: Bool { locations.count >= maxLocations }

    /// The last location cannot be removed.
    var canRemove: Bool { locations.count > 1 }

    /// Reads the saved locations once (detecting them on first launch); later calls wait for it.
    func load() async {
        if loadTask == nil {
            loadTask = Task { [store] in
                let loaded = await store.load()
                if self.locations.isEmpty { self.locations = loaded }
            }
        }
        await loadTask?.value
    }

    /// `(previous, current)` after every saved change; Home reloads from it.
    func onChange(_ listener: @escaping (_ previous: [PodcastLocationItem], _ current: [PodcastLocationItem]) -> Void) {
        listeners.append(listener)
    }

    func add(_ location: PodcastLocationItem) async {
        guard !isFull, !locations.contains(location) else { return }
        await save(locations.map(\.region) + [location.region])
    }

    func remove(_ location: PodcastLocationItem) async {
        guard canRemove else { return }
        await save(locations.map(\.region).filter { $0 != location.region })
    }

    /// Moves the location at `index` one place up (`-1`) or down (`+1`); out of range does nothing.
    func move(at index: Int, by offset: Int) async {
        let target = index + offset
        guard locations.indices.contains(index), locations.indices.contains(target) else { return }
        var regions = locations.map(\.region)
        regions.swapAt(index, target)
        await save(regions)
    }

    /// `List.onMove`.
    func move(fromOffsets source: IndexSet, toOffset destination: Int) async {
        var regions = locations.map(\.region)
        regions.move(fromOffsets: source, toOffset: destination)
        await save(regions)
    }

    private func save(_ regions: [String]) async {
        await load()
        let previous = locations
        let saved = await store.save(regions: regions)
        guard saved != previous else { return }
        locations = saved
        let events = AnalyticsEvents.shared
        analytics.trackEvent(
            name: events.PODCAST_LOCATIONS_CHANGE,
            screen: nil,
            params: events.podcastLocationsChangeParams(regions: saved.map(\.region), previous: previous.map(\.region))
        )
        AppLog.app.notice("podcast_locations_change locations=\(saved.map(\.region).joined(separator: ","), privacy: .public)")
        listeners.forEach { $0(previous, saved) }
    }
}
