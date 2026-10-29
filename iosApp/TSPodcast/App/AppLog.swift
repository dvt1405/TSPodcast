import OSLog

/// OSLog loggers of the app. Never log credentials, request headers or other secrets.
enum AppLog {
    static let subsystem = "tss.t.podcast"

    /// M0 debug screen.
    static let m0 = Logger(subsystem: subsystem, category: "M0")
}
