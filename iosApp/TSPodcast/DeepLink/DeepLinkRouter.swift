import Foundation
import Observation
@preconcurrency import Shared

/// Navigation state of Main that something outside the tabs can drive (a Universal Link): the
/// selected tab, the Discover stack and the Settings sheet. `MainTabView` binds to it.
@MainActor
@Observable
final class MainNavigation {
    var selectedTab: AppTab = .home
    /// Discover stack (Podcast detail, news hub).
    var homePath: [DiscoverRoute] = []
    var isShowingSettings = false

    /// Shows `route` on top of the Discover tab, over whatever sheet was open.
    func revealInDiscover(_ route: PodcastRoute) {
        isShowingSettings = false
        selectedTab = .home
        homePath.append(.podcast(route))
    }
}

/// What an accepted link points to (shared `DeepLink`).
enum AppLink: Hashable, Sendable {
    case podcast(feedId: Int64)
    case episode(id: Int64)

    var logKind: String {
        switch self {
        case .podcast: "podcast"
        case .episode: "episode"
        }
    }

    var logId: Int64 {
        switch self {
        case .podcast(let feedId): feedId
        case .episode(let id): id
        }
    }
}

/// What a resolved link opens: Detail of a podcast, or an episode to play plus its podcast's Detail.
enum DeepLinkTarget: Equatable, Sendable {
    case podcast(PodcastRoute)
    case episode(request: PlaybackRequest, route: PodcastRoute)
}

/// Resolves links to targets. `SharedDeepLinkDataSource` is the real one (shared `DeepLinkTargets`:
/// E11/E12, saved rows, blacklist, D6); unit tests use a fake.
@MainActor
protocol DeepLinkDataSource: AnyObject {
    /// Nil when the link cannot be opened (unknown id, network error, blacklisted, explicit with D6).
    func resolve(_ link: AppLink) async -> DeepLinkTarget?
}

/// Universal Links of the web app (features-spec §3.12, M4 §4.5, plan §7 item 6), fed by
/// `.onOpenURL` and `.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)`:
///
/// - `https://<host>/podcast/<feedId>-<slug>` → the Discover tab shows Podcast detail;
/// - `https://<host>/episode/<id>-<slug>` → E12, the episode plays (the Player stays closed, the
///   MiniPlayer shows) and Detail of its feed is pushed on Discover, like a favourite tap;
/// - a link that cannot be opened → the `deeplink.error` alert.
///
/// `<host>` is Info.plist `TSAppLinkHost` (build setting `APP_LINK_HOST`), empty until the owner has
/// a domain: then no link is accepted. URLs are parsed by shared `DeepLinkParser` (web `parseIdSlug`).
/// A link that arrives before Main (onboarding, category selection, the first routing) is held and
/// opened once Main appears; only the latest held link is kept.
@MainActor
@Observable
final class DeepLinkRouter {
    /// "Không mở được liên kết" alert; the alert's binding writes it back to false.
    var isShowingError = false

    let navigation: MainNavigation
    /// Called before a target is shown (the app closes the full-screen Player).
    @ObservationIgnored var willReveal: () -> Void = {}

    @ObservationIgnored private let parser: DeepLinkParser
    @ObservationIgnored private let dataSource: any DeepLinkDataSource
    @ObservationIgnored private let playback: any PlaybackController
    @ObservationIgnored private let now: () -> Date
    @ObservationIgnored private(set) var isMainActive = false
    @ObservationIgnored private(set) var pending: AppLink?
    /// Latest link being opened; an older resolution that finishes late is dropped.
    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var lastAccepted: (url: URL, at: Date)?

    /// The same URL again within this window is the same open delivered twice (URL + user activity).
    static let duplicateWindow: TimeInterval = 2

    init(
        hosts: [String],
        dataSource: any DeepLinkDataSource,
        playback: any PlaybackController,
        navigation: MainNavigation = MainNavigation(),
        now: @escaping () -> Date = Date.init
    ) {
        parser = DeepLinkParser(allowedHosts: hosts)
        self.dataSource = dataSource
        self.playback = playback
        self.navigation = navigation
        self.now = now
    }

    /// The link of `url`, or nil when it is not one of ours (other host, no host configured,
    /// another path or scheme).
    func link(for url: URL) -> AppLink? {
        switch parser.parse(url: url.absoluteString) {
        case let podcast as DeepLink.Podcast: .podcast(feedId: podcast.feedId)
        case let episode as DeepLink.Episode: .episode(id: episode.episodeId)
        default: nil
        }
    }

    /// Entry point of `.onOpenURL` and `.onContinueUserActivity`. Returns once the link is
    /// ignored, held, or opened (or failed).
    func handle(_ url: URL) async {
        guard let link = link(for: url) else {
            AppLog.deepLink.notice("deeplink_ignored host=\(url.host() ?? "-", privacy: .public)")
            return
        }
        let time = now()
        if let last = lastAccepted, last.url == url, time.timeIntervalSince(last.at) < Self.duplicateWindow {
            return
        }
        lastAccepted = (url, time)
        guard isMainActive else {
            pending = link
            AppLog.deepLink.notice("deeplink_held kind=\(link.logKind, privacy: .public) id=\(link.logId, privacy: .public)")
            return
        }
        await open(link)
    }

    /// Main is on screen: opens the held link, once.
    func mainDidAppear() async {
        guard !isMainActive else { return }
        isMainActive = true
        guard let link = pending else { return }
        pending = nil
        await open(link)
    }

    private func open(_ link: AppLink) async {
        generation += 1
        let current = generation
        let target = await dataSource.resolve(link)
        guard current == generation else { return }
        guard let target else {
            AppLog.deepLink.error("deeplink_failed kind=\(link.logKind, privacy: .public) id=\(link.logId, privacy: .public)")
            isShowingError = true
            return
        }
        AppLog.deepLink.notice("deeplink_open kind=\(link.logKind, privacy: .public) id=\(link.logId, privacy: .public)")
        isShowingError = false
        willReveal()
        switch target {
        case .podcast(let route):
            navigation.revealInDiscover(route)
        case .episode(let request, let route):
            playback.play(request)
            navigation.revealInDiscover(route)
        }
    }
}

/// The configured link host (`TSAppLinkHost`): empty or missing means no link is accepted.
enum AppLinkHost {
    static let infoKey = "TSAppLinkHost"

    static func hosts(from info: [String: Any]) -> [String] {
        let value = (info[infoKey] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return value.isEmpty ? [] : [value]
    }
}
