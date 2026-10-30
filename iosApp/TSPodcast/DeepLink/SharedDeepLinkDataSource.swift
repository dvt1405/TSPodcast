import Foundation
@preconcurrency import Shared

/// `DeepLinkDataSource` over shared `DeepLinkTargets` (E11/E12, saved rows, blacklist, D6). Maps
/// Kotlin values to the Detail route and the play request, and logs failures by type name.
@MainActor
final class SharedDeepLinkDataSource: DeepLinkDataSource {
    private let targets: DeepLinkTargets

    init(targets: DeepLinkTargets) {
        self.targets = targets
    }

    func resolve(_ link: AppLink) async -> DeepLinkTarget? {
        do {
            switch link {
            case .podcast(let feedId):
                let state = try await targets.podcast(feedId: feedId)
                guard let podcast = (state as? TSDataStateSuccess<Podcast>)?.data else {
                    log(link, state)
                    return nil
                }
                return .podcast(PodcastRoute(PodcastCard(podcast)))
            case .episode(let id):
                let state = try await targets.episode(episodeId: id)
                guard let target = (state as? TSDataStateSuccess<EpisodeLinkTarget>)?.data else {
                    log(link, state)
                    return nil
                }
                return Self.episodeTarget(podcast: target.podcast, episode: target.episode)
            }
        } catch {
            AppLog.deepLink.error("deeplink_resolve_failed type=\(String(describing: type(of: error)), privacy: .public)")
            return nil
        }
    }

    /// Like a favourite tap: the episode plays as a one-item playlist, saved with its podcast
    /// (M4 §3.2 "Favourite tap, deep link episode"), and Detail of the feed is pushed.
    static func episodeTarget(podcast: Podcast, episode: Episode) -> DeepLinkTarget {
        let route = PodcastRoute(PodcastCard(podcast))
        let request = PlaybackRequest(
            podcast: route,
            episodes: [EpisodeRow(episode)],
            startIndex: 0,
            history: .playlist(podcast: podcast, episodes: [episode])
        )
        return .episode(request: request, route: route)
    }

    private func log(_ link: AppLink, _ state: Any) {
        let exception = (state as? TSDataStateError<AnyObject>)?.exception
        let reason = (exception as? DeepLinkRefusedException)?.reason.name ?? exception?.typeName ?? "UnexpectedState"
        AppLog.deepLink.notice(
            "deeplink_unavailable kind=\(link.logKind, privacy: .public) id=\(link.logId, privacy: .public) reason=\(reason, privacy: .public)"
        )
    }
}
