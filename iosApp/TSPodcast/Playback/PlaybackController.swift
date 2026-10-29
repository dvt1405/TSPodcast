/// The seam between the screens and the player (audio-playback-ios.md). Screens only call
/// `play`; `AppEnvironment` owns the one instance. M2 uses `LoggingPlaybackController`; the
/// Player milestone (M3) swaps in the AVFoundation player without touching the screens.
@MainActor
protocol PlaybackController: AnyObject {
    /// Starts `request.episodes` from `request.startIndex`, replacing whatever played before.
    func play(_ request: PlaybackRequest)
}
