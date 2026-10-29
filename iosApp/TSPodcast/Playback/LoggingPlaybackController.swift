import Observation
import OSLog

/// `PlaybackController` of M2: nothing plays yet. It keeps the last request (read by the Debug
/// probe of `MainTabView`) and logs `playback_request` with ids and counts only, never a URL.
@MainActor
@Observable
final class LoggingPlaybackController: PlaybackController {
    private(set) var lastRequest: PlaybackRequest?

    func play(_ request: PlaybackRequest) {
        lastRequest = request
        let episodeID = request.startEpisode.map { String($0.id) } ?? "-"
        AppLog.playback.notice(
            "playback_request feedId=\(request.podcast.feedId, privacy: .public) episodeId=\(episodeID, privacy: .public) index=\(request.startIndex, privacy: .public) count=\(request.episodes.count, privacy: .public)"
        )
    }
}
