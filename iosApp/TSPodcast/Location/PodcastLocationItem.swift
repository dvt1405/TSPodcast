import Foundation
@preconcurrency import Shared

/// A podcast location as the UI sees it (shared `PodcastLocation`): a region shown to the user and
/// the one PodcastIndex language its podcasts are listed in. Views never hold Kotlin objects.
struct PodcastLocationItem: Identifiable, Hashable, Sendable {
    /// ISO 3166 region (`VN`).
    let region: String
    /// ISO 639-1 code sent as `lang` (`vi`).
    let contentCode: String
    /// Regional-indicator flag (🇻🇳).
    let flag: String

    var id: String { region }

    /// Country name in `locale` (`Việt Nam`, `Vietnam`); the region code when iOS has no name.
    func name(in locale: Locale = .current) -> String {
        locale.localizedString(forRegionCode: region) ?? region
    }

    /// Language name in its own language, first letter capitalised (`Tiếng Việt`, `English`,
    /// `Português`), like Android `PodcastLocation.languageName`.
    var languageName: String {
        let own = Locale(identifier: contentCode)
        let name = own.localizedString(forLanguageCode: contentCode) ?? contentCode
        return name.prefix(1).uppercased(with: own) + String(name.dropFirst())
    }

    /// "🇻🇳 Việt Nam": the topic page header ("Thịnh hành tại 🇻🇳 Việt Nam").
    func flagAndName(in locale: Locale = .current) -> String {
        "\(flag) \(name(in: locale))"
    }
}

extension PodcastLocationItem {
    init(_ location: PodcastLocation) {
        self.init(region: location.region, contentCode: location.contentCode, flag: location.flag)
    }

    /// The 18 supported locations, Vietnam first (shared `PodcastLocations.SUPPORTED`).
    static var supported: [PodcastLocationItem] {
        PodcastLocations.shared.SUPPORTED.map(PodcastLocationItem.init)
    }

    /// At most 3 (shared `PodcastLocations.MAX_LOCATIONS`).
    static var maxLocations: Int { Int(PodcastLocations.shared.MAX_LOCATIONS) }
}
