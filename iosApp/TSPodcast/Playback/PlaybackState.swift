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
/// M6 adds the playback speed (`speedTenths`, 1.0× for live) and the sleep timer ([iOS+]).
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
    /// Playback speed in tenths (shared `PlaybackSpeed`); always 10 for a live stream.
    var speedTenths = SpeedSteps.defaultTenths
    var sleepTimer = SleepTimerStatus()

    /// The `AVPlayer` rate of `speedTenths`.
    var rate: Double { Double(speedTenths) / 10 }

    var hasPrevious: Bool { current != nil && index > 0 }
    var hasNext: Bool { current != nil && index < queue.count - 1 }

    /// Played fraction, 0 when the duration is unknown or live (shared `progressOf`).
    var progress: Double {
        Double(PlaybackProgressKt.progressOf(positionMs: positionMs, durationMs: durationMs))
    }
}

/// The part of `PlaybackState` that changes on every time-observer tick, published on its own
/// (`AudioEngine.position`) so only the slider, the time labels and the MiniPlayer progress line
/// re-render at 4 Hz.
struct PlaybackPosition: Equatable, Sendable {
    var positionMs: Int64 = 0
    var durationMs: Int64 = 0

    /// Played fraction, the same as `PlaybackState.progress`.
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
