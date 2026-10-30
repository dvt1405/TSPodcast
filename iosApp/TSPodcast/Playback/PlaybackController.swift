/// The seam between the browsing screens and the player (audio-playback-ios.md). Detail only
/// starts playback and reads the state to mark the playing row; the Player and the MiniPlayer
/// drive `AudioEngine` directly. `AppEnvironment` owns the one instance.
@MainActor
protocol PlaybackController: AnyObject {
    /// What plays now (observable on `AudioEngine`).
    var state: PlaybackState { get }

    /// Starts `request.items` from `request.startIndex`, replacing whatever played before.
    func play(_ request: PlaybackRequest)
}
