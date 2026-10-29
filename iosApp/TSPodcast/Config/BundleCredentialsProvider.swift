import Foundation
@preconcurrency import Shared

/// PodcastIndex credentials read from `Info.plist`, which maps the `PODCASTINDEX_*` keys of
/// `Config/Secrets.xcconfig` (gitignored).
///
/// A value that is absent, blank, or still the unexpanded `$(PODCASTINDEX_…)` placeholder is
/// stored as `""`, so shared reports it as missing (`MissingCredentialsException`) instead of
/// signing requests with a placeholder. Values are read once, at init, and never logged.
final class BundleCredentialsProvider: NSObject, CredentialsProvider, Sendable {
    let baseUrl: String
    let apiKey: String
    let apiSecret: String
    let userAgent: String

    init(bundle: Bundle = .main) {
        baseUrl = Self.value(for: "PODCASTINDEX_BASE_URL", in: bundle)
        apiKey = Self.value(for: "PODCASTINDEX_API_KEY", in: bundle)
        apiSecret = Self.value(for: "PODCASTINDEX_API_SECRET", in: bundle)
        userAgent = Self.value(for: "PODCASTINDEX_USER_AGENT", in: bundle)
        super.init()
    }

    /// True when every key has a usable value.
    var isComplete: Bool {
        [baseUrl, apiKey, apiSecret, userAgent].allSatisfy { !$0.isEmpty }
    }

    private static func value(for key: String, in bundle: Bundle) -> String {
        guard let raw = bundle.object(forInfoDictionaryKey: key) as? String else { return "" }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed.contains("$(") { return "" }
        return trimmed
    }
}
