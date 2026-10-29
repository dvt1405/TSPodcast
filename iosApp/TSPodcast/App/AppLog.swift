import OSLog

/// OSLog loggers of the app. Never log credentials, request headers, full URLs (host only) or
/// other secrets.
enum AppLog {
    static let subsystem = "tss.t.podcast"

    /// App flow of M1: launch, routing, onboarding, categories, Home.
    static let app = Logger(subsystem: subsystem, category: "M1")

    /// Analytics calls, until Firebase Analytics exists on iOS (see `LogAnalyticsTracker`).
    static let analytics = Logger(subsystem: subsystem, category: "Analytics")

    /// Cover images (`ArtworkLoader`).
    static let artwork = Logger(subsystem: subsystem, category: "Artwork")
}
