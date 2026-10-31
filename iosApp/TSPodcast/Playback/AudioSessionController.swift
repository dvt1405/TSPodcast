import AVFoundation

/// What the session reports to the player (audio-playback-ios §4).
@MainActor
protocol AudioSessionEventHandling: AnyObject {
    /// A call, Siri or an alarm took the audio; the system already paused the player.
    func audioSessionInterruptionBegan()
    /// The interruption is over; `shouldResume` is the system's hint.
    func audioSessionInterruptionEnded(shouldResume: Bool)
    /// Headphones unplugged or a Bluetooth route went away: HIG says pause.
    func audioSessionOldDeviceUnavailable()
    /// The media server restarted: every AVFoundation object is stale.
    func audioSessionMediaServicesWereReset()
}

/// `AVAudioSession` of the app: `.playback` (keeps playing with the silent switch and in the
/// background), `.spokenAudio` (other apps' spoken prompts pause us instead of ducking) and
/// `.longFormAudio` (AirPlay 2 routing like Apple Podcasts). The session is activated only right
/// before playing, never at launch, so opening the app does not stop other apps' audio.
@MainActor
final class AudioSessionController {
    private let session: AVAudioSession
    private weak var handler: (any AudioSessionEventHandling)?
    private var tokens: [any NSObjectProtocol] = []

    init(session: AVAudioSession = .sharedInstance()) {
        self.session = session
    }

    /// Sets the category and starts listening. Called once by `AudioEngine` (and again after a
    /// media services reset, which drops the category).
    func configure(handler: any AudioSessionEventHandling) {
        self.handler = handler
        applyCategory()
        guard tokens.isEmpty else { return }
        let center = NotificationCenter.default
        tokens = [
            center.addObserver(forName: AVAudioSession.interruptionNotification, object: session, queue: .main) { [weak self] note in
                let info = Self.interruption(note)
                MainActor.assumeIsolated { self?.handleInterruption(info) }
            },
            center.addObserver(forName: AVAudioSession.routeChangeNotification, object: session, queue: .main) { [weak self] note in
                let reason = Self.routeChangeReason(note)
                MainActor.assumeIsolated { self?.handleRouteChange(reason) }
            },
            center.addObserver(forName: AVAudioSession.mediaServicesWereResetNotification, object: session, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { self?.handleMediaServicesReset() }
            },
        ]
    }

    func applyCategory() {
        do {
            try session.setCategory(.playback, mode: .spokenAudio, policy: .longFormAudio)
        } catch {
            AppLog.playback.error("audio_session_category_failed code=\((error as NSError).code, privacy: .public)")
        }
    }

    /// Right before `AVPlayer.play()`.
    func activate() {
        do {
            try session.setActive(true)
        } catch {
            AppLog.playback.error("audio_session_activate_failed code=\((error as NSError).code, privacy: .public)")
        }
    }

    // MARK: - Notifications (internal for unit tests)

    struct Interruption: Equatable, Sendable {
        var type: AVAudioSession.InterruptionType?
        var shouldResume = false
        /// iOS 14.5+: `.began` sent because the app was suspended, not a real interruption.
        var wasSuspended = false
    }

    nonisolated static func interruption(_ note: Notification) -> Interruption {
        let info = note.userInfo ?? [:]
        let type = (info[AVAudioSessionInterruptionTypeKey] as? UInt).flatMap(AVAudioSession.InterruptionType.init(rawValue:))
        let options = AVAudioSession.InterruptionOptions(rawValue: info[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0)
        // `AVAudioSession.InterruptionReason.appWasSuspended` (raw 1) is deprecated since iOS 16
        // but still sent on iOS 17: compare the raw value to keep the build warning-free.
        let reason = info[AVAudioSessionInterruptionReasonKey] as? UInt
        return Interruption(type: type, shouldResume: options.contains(.shouldResume), wasSuspended: reason == 1)
    }

    nonisolated static func routeChangeReason(_ note: Notification) -> AVAudioSession.RouteChangeReason? {
        (note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt).flatMap(AVAudioSession.RouteChangeReason.init(rawValue:))
    }

    func handleInterruption(_ interruption: Interruption) {
        switch interruption.type {
        case .began:
            guard !interruption.wasSuspended else { return }
            AppLog.playback.notice("interruption_began")
            handler?.audioSessionInterruptionBegan()
        case .ended:
            AppLog.playback.notice("interruption_ended resume=\(interruption.shouldResume, privacy: .public)")
            handler?.audioSessionInterruptionEnded(shouldResume: interruption.shouldResume)
        case .none:
            return
        @unknown default:
            return
        }
    }

    func handleRouteChange(_ reason: AVAudioSession.RouteChangeReason?) {
        guard reason == .oldDeviceUnavailable else { return }
        AppLog.playback.notice("route_old_device_unavailable")
        handler?.audioSessionOldDeviceUnavailable()
    }

    func handleMediaServicesReset() {
        AppLog.playback.notice("media_services_reset")
        applyCategory()
        handler?.audioSessionMediaServicesWereReset()
    }
}
