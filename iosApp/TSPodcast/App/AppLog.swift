import OSLog

/// OSLog loggers of the app. Never log credentials, request headers, full URLs (host only) or
/// other secrets.
enum AppLog {
    static let subsystem = "tss.t.podcast"

    /// App flow of M1: launch, routing, onboarding, categories, Home.
    static let app = Logger(subsystem: subsystem, category: "M1")

    /// Analytics calls when Firebase is not configured (see `LogAnalyticsTracker`).
    static let analytics = Logger(subsystem: subsystem, category: "Analytics")

    /// Cover images (`ArtworkLoader`).
    static let artwork = Logger(subsystem: subsystem, category: "Artwork")

    /// App flow of M2: Podcast detail and Search. Never logs a raw search query (length only).
    static let browse = Logger(subsystem: subsystem, category: "M2")

    /// The player (M3): requests, interruptions, failures. Ids and counts only, never URLs.
    static let playback = Logger(subsystem: subsystem, category: "Playback")

    /// Firebase bootstrap and Remote Config (M4): `firebase=enabled` / `firebase=disabled reason=…`.
    static let firebase = Logger(subsystem: subsystem, category: "Firebase")

    /// `OSLogCrashReporter`: breadcrumbs and errors when Crashlytics is not configured.
    static let crash = Logger(subsystem: subsystem, category: "Crash")

    /// Push opt-in, APNs/FCM registration and received notifications (types only, no payload).
    static let notifications = Logger(subsystem: subsystem, category: "Notifications")

    /// Universal Links (M4): kind and id of a link, the host of an ignored one; never the full URL.
    static let deepLink = Logger(subsystem: subsystem, category: "DeepLink")
}
