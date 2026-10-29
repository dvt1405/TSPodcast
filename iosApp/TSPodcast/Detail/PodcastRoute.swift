import Foundation

/// The one navigation value that opens Podcast detail, whatever screen it comes from (Home
/// cards today, Search results and category lists in M2 T3). Each stack registers
/// `navigationDestination(for: PodcastRoute.self)`.
///
/// Android passes the whole `Podcast`; Detail only needs what it shows before E10 answers.
struct PodcastRoute: Hashable, Sendable {
    /// `podcast.id`, the feed id sent to E10/E11.
    let feedId: Int64
    let title: String
    /// Description as plain text (`HtmlText.toPlainText`).
    let summary: String
    /// `podcast.image`, else `feedImage`/`artwork`; nil when all are blank.
    let imageURL: String?
    /// Id of the `matchedTransitionSource` the zoom transition starts from (iOS 18+), unique on
    /// the source screen (the same podcast can sit in two Home sections). Nil: plain push.
    var transitionSourceID: String?
}

extension PodcastRoute {
    /// Route of a Home card; `sourceID` is the card's zoom source.
    init(_ card: PodcastCard, transitionSourceID: String? = nil) {
        self.init(
            feedId: card.id,
            title: card.title,
            summary: card.summary,
            imageURL: card.imageURL,
            transitionSourceID: transitionSourceID
        )
    }
}
