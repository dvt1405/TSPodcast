import AVFoundation
@preconcurrency import Shared

/// Why the current item cannot play. One case for now: the screen only says it failed.
enum PlaybackError: Equatable, Sendable {
    case failed
}

/// What the player publishes, Android's `PlayerControlState` (features-spec §3.6):
/// `isLoading` → `isBuffering`, `currentMediaItem` → `current`, `mediaType` → `isLive`,
/// `currentDuration`/`totalDuration`/`currentProgress` → `positionMs`/`durationMs`/`progress`,
/// `playList` → `queue`, `exception` → `error`. `podcast` is the current item's `podcastTitle`.
struct PlaybackState: Equatable, Sendable {
    var current: PlaybackItem?
    var queue: [PlaybackItem] = []
    var index = 0
    var isPlaying = false
    var isBuffering = false
    var isLive = false
    var positionMs: Int64 = 0
    var durationMs: Int64 = 0
    var isFavourite = false
    var error: PlaybackError?

    var hasPrevious: Bool { current != nil && index > 0 }
    var hasNext: Bool { current != nil && index < queue.count - 1 }

    /// Played fraction, 0 when the duration is unknown or live (shared `progressOf`).
    var progress: Double {
        Double(PlaybackProgressKt.progressOf(positionMs: positionMs, durationMs: durationMs))
    }
}

extension PlaybackState {
    /// `isBuffering` of the Play/Pause spinner: the user wants sound, nothing failed, and AVPlayer
    /// waits for data or the item has not loaded yet.
    static func isBuffering(
        timeControlStatus: AVPlayer.TimeControlStatus,
        itemStatus: AVPlayerItem.Status?,
        wantsToPlay: Bool,
        failed: Bool
    ) -> Bool {
        guard wantsToPlay, !failed else { return false }
        return timeControlStatus == .waitingToPlayAtSpecifiedRate || itemStatus == nil || itemStatus == .unknown
    }

    /// Milliseconds of `time`, 0 when it is not a number (`progressOf` guard against
    /// `C.TIME_UNSET`/NaN on Android): invalid, indefinite, infinite or NaN.
    static func milliseconds(_ time: CMTime) -> Int64 {
        guard time.isNumeric else { return 0 }
        let seconds = time.seconds
        guard seconds.isFinite, seconds > 0 else { return 0 }
        return Int64((seconds * 1000).rounded())
    }

    /// A stream without a duration (live HLS reports `.indefinite`).
    static func isIndefinite(_ duration: CMTime) -> Bool {
        duration.isIndefinite || (duration.isNumeric && !duration.seconds.isFinite)
    }
}
