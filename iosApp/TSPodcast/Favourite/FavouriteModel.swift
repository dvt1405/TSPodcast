import Foundation
import Observation

/// The Favourite tab, Android `FavouriteViewModel` + `FavouriteScreen.onFavouriteSelected`.
///
/// Differences from Android (features-spec §3.8, M4 §3.3):
/// - **[Sửa]** the list follows the table (a favourite set in the Player shows at once); Android
///   read it once and needed the screen to be opened again;
/// - **[Sửa]** a row the database cannot open shows an alert that can remove it; Android's tap
///   did nothing.
///
/// A tap plays the saved episodes from the favourite one and pushes Podcast detail on this tab's
/// stack, without opening the Player (the MiniPlayer shows), like Android.
@MainActor
@Observable
final class FavouriteModel {
    enum State: Equatable, Sendable {
        /// Before the first list arrives (a few ms): nothing is drawn, as on Android.
        case loading
        case empty
        case loaded([FavouriteRow])
    }

    private(set) var state: State = .loading
    /// The tab's navigation stack (Podcast detail of a tapped favourite).
    var path: [PodcastRoute] = []
    /// The row whose tap found nothing to play: "Không mở được mục này" alert.
    var unavailable: FavouriteRow?

    @ObservationIgnored private let dataSource: any FavouriteDataSource
    @ObservationIgnored private let playback: any PlaybackController
    @ObservationIgnored private var observation: FavouriteObservation?
    /// A tap is being resolved; another tap meanwhile is ignored.
    @ObservationIgnored private var isResolving = false

    init(dataSource: any FavouriteDataSource, playback: any PlaybackController) {
        self.dataSource = dataSource
        self.playback = playback
        // Observed for the model's lifetime (the tab lives as long as Main), so the list is
        // current before the tab is first shown.
        observation = dataSource.observe { [weak self] rows in
            self?.apply(rows)
        }
    }

    /// Rows on screen; empty unless `.loaded`.
    var rows: [FavouriteRow] {
        if case .loaded(let rows) = state { return rows }
        return []
    }

    /// Tap on row `index`: plays it and pushes its podcast, or shows the unavailable alert.
    func select(index: Int) async {
        let rows = rows
        guard rows.indices.contains(index), !isResolving else { return }
        isResolving = true
        defer { isResolving = false }
        let row = rows[index]
        guard let playback = await dataSource.resolve(row) else {
            unavailable = row
            return
        }
        self.playback.play(playback.request)
        path.append(playback.route)
    }

    /// "Bỏ yêu thích" of the alert: the row leaves the list through the observation.
    func removeUnavailable() async {
        guard let row = unavailable else { return }
        unavailable = nil
        await dataSource.remove(row)
    }

    /// "Đóng" of the alert.
    func dismissUnavailable() {
        unavailable = nil
    }

    private func apply(_ rows: [FavouriteRow]) {
        state = rows.isEmpty ? .empty : .loaded(rows)
        if let unavailable, !rows.contains(unavailable) {
            self.unavailable = nil
        }
    }
}
