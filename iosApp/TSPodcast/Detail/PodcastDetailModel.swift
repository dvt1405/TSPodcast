import Foundation
import Observation
@preconcurrency import Shared

/// Podcast detail, Android's `PodcastViewModel` for one pushed screen: loads the episodes once,
/// shows an alert then an inline error on failure, and hands taps to the player seam.
///
/// Differences from Android (features-spec §3.5 [Sửa]): an empty list is `.empty` (Android
/// shimmers forever), a failure keeps `.failed` after the alert is dismissed (Android went back
/// to `Init` and shimmered forever), and the alert text is localized instead of the raw message.
///
/// "Tìm trong show" (M6 §3.3, N5) when the show has more than 20 episodes: the first query loads
/// up to 1 000 episodes once (`searchIndex`), every query is filtered 200 ms after the last
/// keystroke, without accents. A tap on a result plays the results list.
@MainActor
@Observable
final class PodcastDetailModel {
    enum State: Equatable, Sendable {
        case loading
        case loaded([EpisodeRow])
        case empty
        /// D6 hid the whole feed (E11 `explicit == true`).
        case explicitHidden
        case failed
    }

    /// "Tìm trong show" while the search field has text.
    enum SearchState: Equatable, Sendable {
        /// No query: the normal list.
        case idle
        /// The first query waits for the 1 000-episode index.
        case loading
        case results([EpisodeRow])
        /// "Không tìm thấy tập phù hợp".
        case noMatch
    }

    /// Search shows only for a show with more than this many episodes.
    static let searchThreshold = 20

    let podcast: PodcastRoute
    private(set) var state: State = .loading
    /// The list comes from the database because E10 failed (M4 §3.3 [Sửa]): `detail.offlineBanner`.
    private(set) var isShowingSavedEpisodes = false
    /// "Rất tiếc!" alert of a failed load; the alert's binding writes it back to false.
    var isShowingErrorAlert = false
    /// What the search field shows, as typed.
    private(set) var searchQuery = ""
    private(set) var searchState: SearchState = .idle
    /// Set when the show has more episodes than the search covers: "Chỉ tìm trong %d tập mới nhất".
    private(set) var searchFootnoteCount: Int?
    /// The 1 000-episode request failed: the search covers the episodes on screen only.
    private(set) var isSearchingOnScreenOnly = false
    /// `feed.episodeCount` of the loaded content.
    private(set) var feedEpisodeCount: Int?

    @ObservationIgnored private let dataSource: any PodcastDetailDataSource
    @ObservationIgnored private let playback: any PlaybackController
    @ObservationIgnored private var hasStarted = false
    /// Room rows of the loaded list, saved when an episode plays (M4 §3.2).
    @ObservationIgnored private var history: PlaybackHistorySource?
    /// Latest request; an older result that arrives late is dropped.
    @ObservationIgnored private var latestRequest = 0
    @ObservationIgnored private let analytics: (any AnalyticsTracker)?
    @ObservationIgnored private let clock: any Clock<Duration>
    @ObservationIgnored private let debounce: Duration
    /// The loaded content, kept as the search fallback.
    @ObservationIgnored private var content: PodcastDetailContent?
    /// One index per Detail screen: the first query starts it, later ones reuse it.
    @ObservationIgnored private var searchIndexTask: Task<ShowSearchIndexResult, Never>?
    @ObservationIgnored private var searchTask: Task<Void, Never>?
    /// Matches behind `.results`, for the playlist and history of a tap.
    @ObservationIgnored private var searchMatches = ShowSearchMatches(episodes: [])
    @ObservationIgnored private var hasLoggedSearch = false

    init(
        podcast: PodcastRoute,
        dataSource: any PodcastDetailDataSource,
        playback: any PlaybackController,
        analytics: (any AnalyticsTracker)? = nil,
        clock: any Clock<Duration> = ContinuousClock(),
        debounce: Duration = .milliseconds(200)
    ) {
        self.podcast = podcast
        self.dataSource = dataSource
        self.playback = playback
        self.analytics = analytics
        self.clock = clock
        self.debounce = debounce
    }

    /// Episodes on screen, in order; empty unless `.loaded`.
    var episodes: [EpisodeRow] {
        if case .loaded(let rows) = state { return rows }
        return []
    }

    /// First load, once per pushed screen: `.task` runs again when the view reappears (coming
    /// back from another tab or screen) and must not send a new request.
    func loadIfNeeded() async {
        guard !hasStarted else { return }
        hasStarted = true
        await load()
    }

    /// "Tải lại" of the alert or of the inline error.
    func retry() async {
        hasStarted = true
        await load()
    }

    /// "Huỷ" of the alert: the screen stays on the inline error with its own "Tải lại".
    func dismissError() {
        isShowingErrorAlert = false
    }

    /// Tap on episode `index`: the whole list and the position go to the player (Android
    /// `playerEpisode(episode, podcast, listItems)`). Returns false, doing nothing, unless
    /// episodes are shown; the caller then opens the Player.
    @discardableResult
    func play(index: Int) -> Bool {
        let rows = episodes
        guard rows.indices.contains(index) else { return false }
        playback.play(PlaybackRequest(podcast: podcast, episodes: rows, startIndex: index, history: history))
        return true
    }

    // MARK: - Search in show (M6 §3.3)

    /// Whether the search field shows: the list is loaded and the show has more than 20 episodes
    /// (listed, or announced by E11).
    var isSearchAvailable: Bool {
        guard case .loaded(let rows) = state else { return false }
        return rows.count > Self.searchThreshold || (feedEpisodeCount ?? 0) > Self.searchThreshold
    }

    /// The search field changed. A blank query goes back to the list at once; any other text is
    /// filtered once `debounce` has passed without another change.
    func searchQueryChanged(_ text: String) {
        guard text != searchQuery else { return }
        searchQuery = text
        searchTask?.cancel()
        let query = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else {
            searchTask = nil
            searchState = .idle
            return
        }
        if searchState == .idle {
            searchState = .loading
        }
        let clock = clock
        let debounce = debounce
        searchTask = Task { [weak self] in
            do {
                try await clock.sleep(for: debounce)
            } catch {
                return
            }
            guard let self, !Task.isCancelled else { return }
            await self.runSearch(query)
        }
    }

    /// Tap on search result `index`: the results list and that position go to the player.
    @discardableResult
    func playSearchResult(index: Int) -> Bool {
        guard case .results(let rows) = searchState, rows.indices.contains(index) else { return false }
        playback.play(PlaybackRequest(
            podcast: podcast,
            episodes: rows,
            startIndex: index,
            history: searchMatches.history(basedOn: history)
        ))
        return true
    }

    private func runSearch(_ query: String) async {
        let result = await searchIndex().value
        guard !Task.isCancelled, query == searchQuery.trimmingCharacters(in: .whitespacesAndNewlines) else { return }
        let matches = result.index.matches(query)
        searchMatches = matches
        isSearchingOnScreenOnly = result.isFallback
        searchFootnoteCount = result.index.isTruncated ? result.index.loadedCount : nil
        searchState = matches.episodes.isEmpty ? .noMatch : .results(matches.episodes)
        logFirstSearch(resultCount: matches.episodes.count, truncated: result.index.isTruncated)
    }

    /// The index of this screen, requested by the first query only.
    private func searchIndex() -> Task<ShowSearchIndexResult, Never> {
        if let searchIndexTask { return searchIndexTask }
        let dataSource = dataSource
        let feedId = podcast.feedId
        let onScreen = content ?? PodcastDetailContent(episodes: episodes)
        let task = Task { await dataSource.searchIndex(feedId: feedId, onScreen: onScreen) }
        searchIndexTask = task
        return task
    }

    /// `show_search` once per Detail screen, for the first query with a result (never the query).
    private func logFirstSearch(resultCount: Int, truncated: Bool) {
        guard !hasLoggedSearch else { return }
        hasLoggedSearch = true
        let events = AnalyticsEvents.shared
        analytics?.trackEvent(
            name: events.SHOW_SEARCH,
            screen: PodcastDetailView.analyticsScreen,
            params: [events.PARAM_RESULT_COUNT: String(resultCount), events.PARAM_TRUNCATED: String(truncated)]
        )
    }

    /// Id of the episode of this podcast the player holds (playing or paused), for the
    /// "Đang phát" mark; nil when the player holds nothing or another podcast's episode.
    var nowPlayingEpisodeID: Int64? {
        guard let current = playback.state.current, current.feedId == podcast.feedId else { return nil }
        return Int64(current.id)
    }

    private func load() async {
        latestRequest += 1
        let request = latestRequest
        state = .loading
        isShowingErrorAlert = false
        isShowingSavedEpisodes = false
        history = nil
        let result = await dataSource.load(feedId: podcast.feedId)
        guard request == latestRequest else { return }
        switch result {
        case .success(let content):
            self.content = content
            feedEpisodeCount = content.feedEpisodeCount
            history = content.source.map { PlaybackHistorySource.detail($0.value, route: podcast) }
            isShowingSavedEpisodes = content.isFromCache && !content.episodes.isEmpty
            if content.isExplicitFeedHidden {
                state = .explicitHidden
            } else if content.episodes.isEmpty {
                state = .empty
            } else {
                state = .loaded(content.episodes)
            }
        case .failure:
            state = .failed
            isShowingErrorAlert = true
        }
    }
}
