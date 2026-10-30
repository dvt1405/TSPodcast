import Foundation
import Observation

/// Podcast detail, Android's `PodcastViewModel` for one pushed screen: loads the episodes once,
/// shows an alert then an inline error on failure, and hands taps to the player seam.
///
/// Differences from Android (features-spec §3.5 [Sửa]): an empty list is `.empty` (Android
/// shimmers forever), a failure keeps `.failed` after the alert is dismissed (Android went back
/// to `Init` and shimmered forever), and the alert text is localized instead of the raw message.
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

    let podcast: PodcastRoute
    private(set) var state: State = .loading
    /// The list comes from the database because E10 failed (M4 §3.3 [Sửa]): `detail.offlineBanner`.
    private(set) var isShowingSavedEpisodes = false
    /// "Rất tiếc!" alert of a failed load; the alert's binding writes it back to false.
    var isShowingErrorAlert = false

    @ObservationIgnored private let dataSource: any PodcastDetailDataSource
    @ObservationIgnored private let playback: any PlaybackController
    @ObservationIgnored private var hasStarted = false
    /// Room rows of the loaded list, saved when an episode plays (M4 §3.2).
    @ObservationIgnored private var history: PlaybackHistorySource?
    /// Latest request; an older result that arrives late is dropped.
    @ObservationIgnored private var latestRequest = 0

    init(podcast: PodcastRoute, dataSource: any PodcastDetailDataSource, playback: any PlaybackController) {
        self.podcast = podcast
        self.dataSource = dataSource
        self.playback = playback
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
