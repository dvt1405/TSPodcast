import Foundation
import Observation

/// The Home sections in display order: Android's (`DiscoverPodcastsScreen`, without the ad slots)
/// with "Thời sự & Bản tin" right after trending (M6 §3.4), then "Podcast theo vị trí". The raw
/// value is the accessibility id part: `home.section.<rawValue>`.
enum HomeSection: String, CaseIterable, Sendable {
    case trending
    /// "Thời sự & Bản tin": the curated hub for the primary location, catalog news for the others;
    /// absent while nothing has news.
    case news
    /// "Podcast theo vị trí": one chip per podcast location, a top pick and a ranked grid.
    case location
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

/// State of the "Thời sự & Bản tin" section. Unlike the other sections it has no empty state:
/// while the hub is off, or nothing loaded, the section is not there at all (M6 §3.4).
enum NewsSectionState: Hashable, Sendable {
    case absent
    case loading
    case loaded(NewsHubContent)
    case failed

    var content: NewsHubContent? {
        if case .loaded(let content) = self { return content }
        return nil
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
    /// "Thời sự & Bản tin" (M6 §3.4): the curated hub, which replaces the primary location's
    /// catalog news when it has news (Android `DiscoverPodcastsScreen`).
    private(set) var news: NewsSectionState
    /// "Podcast theo vị trí" per location region; a region missing here is still loading.
    private(set) var locationPodcasts: [String: SectionState<PodcastCard>] = [:]
    /// "Thời sự" from the catalog per location region; a region missing here is still loading.
    private(set) var locationNews: [String: [NewsItem]] = [:]
    /// Completed full loads: 1 after the first load, +1 per refresh (`home.list` value in Debug).
    private(set) var generation = 0

    /// One call of the data source; trending and forYou share one (`trendingSections`).
    enum Loader: CaseIterable, Sendable {
        case trendingSections
        case music
        case live
        case recent
        /// The news hub (`NewsHubDataSource`).
        case news
        /// "Podcast theo vị trí" and catalog "Thời sự", one call per location each.
        case locations
    }

    @ObservationIgnored private let dataSource: any HomeDataSource
    @ObservationIgnored private let newsHub: (any NewsHubDataSource)?
    @ObservationIgnored private let locationsModel: PodcastLocationsModel?
    @ObservationIgnored private var hasStarted = false
    /// Latest request per loader; an older result that arrives late is dropped.
    @ObservationIgnored private var latestRequest: [Loader: Int] = [:]
    /// Latest request per location list (`podcasts.<region>`, `news.<region>`).
    @ObservationIgnored private var latestLocationRequest: [String: Int] = [:]

    init(dataSource: any HomeDataSource, newsHub: (any NewsHubDataSource)? = nil, locations: PodcastLocationsModel? = nil) {
        self.dataSource = dataSource
        self.newsHub = newsHub
        locationsModel = locations
        news = newsHub?.isEnabled == true ? .loading : .absent
    }

    /// The podcast locations in priority order (empty without a locations model or before loading).
    var locations: [PodcastLocationItem] { locationsModel?.locations ?? [] }

    /// What "Podcast theo vị trí" shows for `region`: the primary location's list without the first
    /// 10 trending shows (already on screen) while enough are left (Android: more than 14).
    func displayedLocationPodcasts(_ region: String) -> SectionState<PodcastCard>? {
        guard let state = locationPodcasts[region] else { return nil }
        guard region == locations.first?.region, case .loaded(let cards) = state,
              case .loaded(let trendingCards) = trending else { return state }
        return .loaded(Self.withoutTopTrending(cards, trending: trendingCards))
    }

    /// `cards` without the first 10 of `trending`, unless that leaves 4 or fewer of them.
    static func withoutTopTrending(_ cards: [PodcastCard], trending: [PodcastCard]) -> [PodcastCard] {
        let shown = Set(trending.prefix(10).map(\.id))
        return cards.count > shown.count + 4 ? cards.filter { !shown.contains($0.id) } : cards
    }

    /// Catalog news per region, with the hub's "Mới nhất" in place of the primary location's when
    /// the hub has news. The regions with news, in location order, are the section's chips.
    func newsRegions() -> [PodcastLocationItem] {
        let primary = locations.first?.region
        return locations.filter { location in
            (location.region == primary && news.content != nil) || !(locationNews[location.region] ?? []).isEmpty
        }
    }

    /// A change of the podcast locations (Android `MainViewModel.init`): another primary location
    /// reloads every list in its language (blank first, like a language switch); a reorder or a
    /// secondary change reloads only the per-location lists.
    func locationsChanged(from previous: [PodcastLocationItem], to current: [PodcastLocationItem]) async {
        switch PodcastLocationChange.between(previous, current) {
        case .none:
            return
        case .primary:
            hasStarted = true
            await loadAll(keepingContent: false)
        case .secondary:
            await loadLocations(force: false, keepingContent: true)
        }
    }

    /// "Tải lại" of one location's list.
    func retryLocation(_ region: String) async {
        guard let location = locations.first(where: { $0.region == region }) else { return }
        await loadPodcasts(in: location, force: true, keepingContent: false)
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
        case .news: .news
        case .location: .locations
        }
    }

    /// "Ẩn show này" (M6 §3.5): drops `feedId` from every section at once, without a request
    /// (shared `HomeFeed` already leaves it out of the next load). A section left with nothing
    /// shows its empty state.
    func removeShow(feedId: Int64) {
        trending = Self.removing(feedId, from: trending)
        music = Self.removing(feedId, from: music)
        recent = Self.removing(feedId, from: recent)
        forYou = Self.removing(feedId, from: forYou)
        locationPodcasts = locationPodcasts.mapValues { Self.removing(feedId, from: $0) }
        locationNews = locationNews.mapValues { items in items.filter { $0.feedId != feedId } }
        if let content = news.content {
            let kept = content.removing(feedId: feedId)
            news = kept.hasNews ? .loaded(kept) : .absent
        }
        if case .loaded(let pages) = live {
            let items = pages.flatMap { $0 }
            let kept = items.filter { $0.feedId != feedId }
            if kept.count != items.count {
                live = LivePaging.state(.success(kept))
            }
        }
    }

    private static func removing(_ feedId: Int64, from state: SectionState<PodcastCard>) -> SectionState<PodcastCard> {
        guard case .loaded(let cards) = state else { return state }
        let kept = cards.filter { $0.id != feedId }
        guard kept.count != cards.count else { return state }
        return kept.isEmpty ? .empty : .loaded(kept)
    }

    // MARK: - Loading

    private func loadAll(keepingContent: Bool) async {
        // The locations first: the shared lists read the primary one's language, and the
        // per-location lists need them (detected and saved on first launch).
        await locationsModel?.load()
        async let trendingSections: Void = load(.trendingSections, keepingContent: keepingContent)
        async let music: Void = load(.music, keepingContent: keepingContent)
        async let live: Void = load(.live, keepingContent: keepingContent)
        async let recent: Void = load(.recent, keepingContent: keepingContent)
        async let news: Void = load(.news, keepingContent: keepingContent)
        // Pull-to-refresh skips their 15-minute cache (Android `reload`: `force = true`).
        async let locations: Void = loadLocations(force: keepingContent, keepingContent: keepingContent)
        _ = await (trendingSections, music, live, recent, news, locations)
        generation += 1
        AppLog.app.notice("home_loaded generation=\(self.generation, privacy: .public)")
    }

    /// Both per-location lists of every location, in parallel; regions no longer chosen are dropped.
    private func loadLocations(force: Bool, keepingContent: Bool) async {
        let current = locations
        let regions = Set(current.map(\.region))
        locationPodcasts = locationPodcasts.filter { regions.contains($0.key) }
        locationNews = locationNews.filter { regions.contains($0.key) }
        guard !current.isEmpty else { return }
        await withTaskGroup(of: Void.self) { group in
            for location in current {
                group.addTask { await self.loadPodcasts(in: location, force: force, keepingContent: keepingContent) }
                group.addTask { await self.loadNews(in: location, force: force, keepingContent: keepingContent) }
            }
        }
    }

    private func loadPodcasts(in location: PodcastLocationItem, force: Bool, keepingContent: Bool) async {
        let key = "podcasts.\(location.region)"
        let request = (latestLocationRequest[key] ?? 0) + 1
        latestLocationRequest[key] = request
        let current = locationPodcasts[location.region] ?? .loading
        if !(keepingContent && current.isLoaded) {
            locationPodcasts[location.region] = nil
        }
        let result = await dataSource.podcasts(in: location, force: force)
        guard latestLocationRequest[key] == request, locations.contains(location) else { return }
        locationPodcasts[location.region] = Self.next(current, SectionState(result), keepingContent)
    }

    private func loadNews(in location: PodcastLocationItem, force: Bool, keepingContent: Bool) async {
        let key = "news.\(location.region)"
        let request = (latestLocationRequest[key] ?? 0) + 1
        latestLocationRequest[key] = request
        if !keepingContent {
            locationNews[location.region] = nil
        }
        let items = await dataSource.news(in: location, force: force)
        guard latestLocationRequest[key] == request, locations.contains(location) else { return }
        // A refresh that brings nothing keeps what is shown (as the other sections do on failure).
        if keepingContent, items.isEmpty, !(locationNews[location.region] ?? []).isEmpty { return }
        locationNews[location.region] = items
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
        case .news:
            await loadNews(request: request, keepingContent: keepingContent)
        case .locations:
            await loadLocations(force: !keepingContent, keepingContent: keepingContent)
        }
    }

    /// The hub may have been turned on or off by Remote Config since the last load, so each load
    /// asks again. A failed refresh keeps the content on screen, like the other sections.
    private func loadNews(request: Int, keepingContent: Bool) async {
        guard let newsHub, newsHub.isEnabled else {
            news = .absent
            return
        }
        if !(keepingContent && news.content != nil) {
            news = .loading
        }
        let result = await newsHub.load()
        guard latestRequest[.news] == request else { return }
        switch result {
        case .success(let content?):
            news = .loaded(content)
        case .success(nil):
            news = .absent
        case .failure:
            if !(keepingContent && news.content != nil) {
                news = .failed
            }
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
