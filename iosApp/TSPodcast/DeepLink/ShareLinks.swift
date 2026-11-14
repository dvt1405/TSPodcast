import Foundation

/// Share links on the TSPodcast landing site, identical to Android `WebLinks`:
/// `https://tspodcast.web.app/podcast/<id>-<slug>` and `/episode/<id>-<slug>`. With the app
/// installed they open it (Universal Links, `/.well-known/apple-app-site-association` on the site,
/// routed by `DeepLinkRouter`); otherwise the site shows an "open in the app" page.
enum ShareLinks {
    static let host = "tspodcast.web.app"

    static func podcast(id: Int64, title: String?) -> URL {
        URL(string: "https://\(host)/podcast/\(id)-\(slugify(title))")!
    }

    static func episode(id: Int64, title: String?) -> URL {
        URL(string: "https://\(host)/episode/\(id)-\(slugify(title))")!
    }

    /// Lowercase ASCII words joined by `-`, Vietnamese marks removed (`đ` → `d`), at most
    /// [maxLength] characters; "podcast" when nothing is left (Android `WebLinks.slugify`).
    static func slugify(_ input: String?, maxLength: Int = 80) -> String {
        let folded = (input ?? "")
            .replacingOccurrences(of: "đ", with: "d")
            .replacingOccurrences(of: "Đ", with: "D")
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US_POSIX"))
            .lowercased()
        var slug = ""
        var pendingDash = false
        for scalar in folded.unicodeScalars {
            if ("a"..."z").contains(scalar) || ("0"..."9").contains(scalar) {
                if pendingDash && !slug.isEmpty { slug.append("-") }
                pendingDash = false
                slug.unicodeScalars.append(scalar)
            } else {
                pendingDash = true
            }
        }
        if slug.count > maxLength {
            slug = String(slug.prefix(maxLength))
            while slug.hasSuffix("-") { slug.removeLast() }
        }
        return slug.isEmpty ? "podcast" : slug
    }

    /// "Nghe "<title>" trên TSPodcast: <link>" in the app language.
    static func podcastText(title: String, url: URL) -> String {
        String(format: String(localized: "share.podcast.text"), title, url.absoluteString)
    }

    static func episodeText(title: String, url: URL) -> String {
        String(format: String(localized: "share.episode.text"), title, url.absoluteString)
    }
}
