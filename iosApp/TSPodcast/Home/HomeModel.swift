import Foundation
import Observation

/// The five Home sections, in Android's order (`DiscoverPodcastsScreen`, without the ad slots).
/// The raw value is the accessibility id part: `home.section.<rawValue>`.
enum HomeSection: String, CaseIterable, Sendable {
    case trending
    case music
    case live
    case recent
    case forYou
}

/// State of one Home section. Unlike Android, where a failed Live/Recent section shimmers
/// forever, a section ends in content, `empty` or `failed`, and the last two offer "Tải lại"
/// (features-spec §3.4 [Sửa]).
enum SectionState<Item: Hashable & Sendable>: Hashable, Sendable {
    case loading
    case loaded([Item])
    case empty
    case failed

    init(_ result: Result<[Item], HomeLoadError>) {
        switch result {
        case .success(let items): self = items.isEmpty ? .empty : .loaded(items)
        case .failure: self = .failed
        }
    }

    var isLoaded: Bool {
        if case .loaded = self { return true }
        return false
    }
}

/// Live items as pager pages of `pageSize` rows (Android `liveEpisode.chunked(3)`).
enum LivePaging {
    static let pageSize = 3

    static func pages(_ items: [LiveRowItem]) -> [[LiveRowItem]] {
        stride(from: 0, to: items.count, by: pageSize).map { start in
            Array(items[start..<min(start + pageSize, items.count)])
        }
    }

    /// An empty list is `.empty`, not a pager without pages.
    static func state(_ result: Result<[LiveRowItem], HomeLoadError>) -> SectionState<[LiveRowItem]> {
        SectionState(result.map(pages))
    }
}

/// Home/Discover, Android's `MainViewModel` (Home part): loads the sections in parallel, each
/// with its own state, retries one section at a time and reloads everything on pull-to-refresh.
///
/// Refresh keeps what a section already shows until the new result arrives (iOS style; Android
/// blanks every section) and keeps it when that refresh fails; a section without content goes
/// back to the shimmer. The 15-minute trending cache lives in shared and still applies.
@MainActor
@Observable
final class HomeModel {
    private(set) var trending: SectionState<PodcastCard> = .loading
    private(set) var music: SectionState<PodcastCard> = .loading
    /// Pages of at most 3 rows.
    private(set) var live: SectionState<[LiveRowItem]> = .loading
    private(set) var recent: SectionState<PodcastCard> = .loading
    private(set) var forYou: SectionState<PodcastCard> = .loading
    /// Completed full loads: 1 after the first load, +1 per refresh (`home.list` value in Debug).
    private(set) var generation = 0

    /// One call of the data source; trending and forYou share one (`trendingSections`).
    enum Loader: CaseIterable, Sendable {
        case trendingSections
        case music
        case live
        case recent
    }

    @ObservationIgnored private let dataSource: any HomeDataSource
    @ObservationIgnored private var hasStarted = false
    /// Latest request per loader; an older result that arrives late is dropped.
    @ObservationIgnored private var latestRequest: [Loader: Int] = [:]

    init(dataSource: any HomeDataSource) {
        self.dataSource = dataSource
    }

    /// First load, once per model (`.task` may run again when the view reappears).
    func loadIfNeeded() async {
        guard !hasStarted else { return }
        hasStarted = true
        await loadAll(keepingContent: false)
    }

    /// Pull-to-refresh (`MainViewModel.reload`): every loader runs again.
    func refresh() async {
        hasStarted = true
        await loadAll(keepingContent: true)
    }

    /// "Tải lại" of one section: only its loader runs again (trending and forYou share one).
    func retry(_ section: HomeSection) async {
        await load(Self.loader(for: section), keepingContent: false)
    }

    static func loader(for section: HomeSection) -> Loader {
        switch section {
        case .trending, .forYou: .trendingSections
        case .music: .music
        case .live: .live
        case .recent: .recent
        }
    }

    // MARK: - Loading

    private func loadAll(keepingContent: Bool) async {
        async let trendingSections: Void = load(.trendingSections, keepingContent: keepingContent)
        async let music: Void = load(.music, keepingContent: keepingContent)
        async let live: Void = load(.live, keepingContent: keepingContent)
        async let recent: Void = load(.recent, keepingContent: keepingContent)
        _ = await (trendingSections, music, live, recent)
        generation += 1
        AppLog.app.notice("home_loaded generation=\(self.generation, privacy: .public)")
    }

    private func load(_ loader: Loader, keepingContent: Bool) async {
        let request = (latestRequest[loader] ?? 0) + 1
        latestRequest[loader] = request
        let dataSource = dataSource

        switch loader {
        case .trendingSections:
            trending = Self.waiting(trending, keepingContent)
            forYou = Self.waiting(forYou, keepingContent)
            let result = await dataSource.trendingSections()
            guard latestRequest[loader] == request else { return }
            trending = Self.next(trending, SectionState(result.trending), keepingContent)
            forYou = Self.next(forYou, SectionState(result.forYou), keepingContent)
        case .music:
            music = Self.waiting(music, keepingContent)
            let result = await dataSource.music()
            guard latestRequest[loader] == request else { return }
            music = Self.next(music, SectionState(result), keepingContent)
        case .live:
            live = Self.waiting(live, keepingContent)
            let result = await dataSource.live()
            guard latestRequest[loader] == request else { return }
            live = Self.next(live, LivePaging.state(result), keepingContent)
        case .recent:
            recent = Self.waiting(recent, keepingContent)
            let result = await dataSource.recent()
            guard latestRequest[loader] == request else { return }
            recent = Self.next(recent, SectionState(result), keepingContent)
        }
    }

    /// State while a request runs: loaded content stays during a refresh, anything else shimmers.
    private static func waiting<Item>(_ current: SectionState<Item>, _ keepingContent: Bool) -> SectionState<Item> {
        keepingContent && current.isLoaded ? current : .loading
    }

    /// A failed refresh does not replace content the section already shows.
    private static func next<Item>(
        _ current: SectionState<Item>,
        _ result: SectionState<Item>,
        _ keepingContent: Bool
    ) -> SectionState<Item> {
        if keepingContent, current.isLoaded, result == .failed { return current }
        return result
    }
}
