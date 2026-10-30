/// A screen pushed on the Discover (Khám phá) stack: Podcast detail from any card, or the news
/// hub (M6 §3.4) from the Home section. The other tabs push `PodcastRoute` directly.
enum DiscoverRoute: Hashable, Sendable {
    case podcast(PodcastRoute)
    /// "Thời sự & Bản tin", and how it was opened (`news_hub_open` `from`).
    case newsHub(NewsHubEntry)

    /// The podcast of a `.podcast` route.
    var podcast: PodcastRoute? {
        if case .podcast(let route) = self { return route }
        return nil
    }
}
