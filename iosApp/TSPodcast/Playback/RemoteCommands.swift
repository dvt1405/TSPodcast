import MediaPlayer

/// What remote commands drive (`AudioEngine`; a fake in unit tests).
@MainActor
protocol RemoteCommandHandling: AnyObject {
    func play()
    func pause()
    func togglePlayPause()
    func seek(toMs positionMs: Int64)
    /// −15 / +30 s from the Lock Screen, Control Center, headphones and CarPlay.
    func skip(bySeconds seconds: Double)
    /// A rate picked on the Lock Screen (any value: the engine normalizes it).
    func changeRate(_ rate: Float)
}

/// `MPRemoteCommandCenter` (audio-playback-ios §5.2, M3 §4.5, M6 §3.1): play/pause/toggle,
/// skip −15 / +30 s, the speed and dragging the Lock Screen bar. Next/previous track stay
/// **disabled**: with both enabled the system shows them instead of the skip buttons (M6 §7
/// item 5; the Player keeps them in the Up next panel). Seek forward/backward are not offered.
@MainActor
final class RemoteCommands {
    static let skipBackwardSeconds: Double = 15
    static let skipForwardSeconds: Double = 30

    private let center: MPRemoteCommandCenter
    private weak var handler: (any RemoteCommandHandling)?
    private var targets: [(MPRemoteCommand, Any)] = []

    init(center: MPRemoteCommandCenter = .shared()) {
        self.center = center
    }

    func install(handler: any RemoteCommandHandling) {
        uninstall()
        self.handler = handler
        add(center.playCommand) { $0.handlePlay() }
        add(center.pauseCommand) { $0.handlePause() }
        add(center.togglePlayPauseCommand) { $0.handleTogglePlayPause() }
        center.skipBackwardCommand.preferredIntervals = [NSNumber(value: Self.skipBackwardSeconds)]
        center.skipForwardCommand.preferredIntervals = [NSNumber(value: Self.skipForwardSeconds)]
        let backward = center.skipBackwardCommand
        targets.append((backward, backward.addTarget { [weak self] event in
            let interval = (event as? MPSkipIntervalCommandEvent)?.interval ?? Self.skipBackwardSeconds
            return MainActor.assumeIsolated { self?.handleSkip(seconds: -interval) ?? .commandFailed }
        }))
        let forward = center.skipForwardCommand
        targets.append((forward, forward.addTarget { [weak self] event in
            let interval = (event as? MPSkipIntervalCommandEvent)?.interval ?? Self.skipForwardSeconds
            return MainActor.assumeIsolated { self?.handleSkip(seconds: interval) ?? .commandFailed }
        }))
        center.changePlaybackRateCommand.supportedPlaybackRates = SpeedSteps.remoteRates.map { NSNumber(value: $0) }
        let rate = center.changePlaybackRateCommand
        targets.append((rate, rate.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackRateCommandEvent else { return .commandFailed }
            let playbackRate = event.playbackRate
            return MainActor.assumeIsolated { self?.handleChangeRate(playbackRate) ?? .commandFailed }
        }))
        let position = center.changePlaybackPositionCommand
        targets.append((position, position.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            let seconds = event.positionTime
            return MainActor.assumeIsolated { self?.handleChangePosition(seconds: seconds) ?? .commandFailed }
        }))
        for command in Self.unsupported(center) {
            command.isEnabled = false
        }
        configure(for: PlaybackState())
    }

    func uninstall() {
        for (command, target) in targets {
            command.removeTarget(target)
        }
        targets = []
    }

    /// Enables what the current state allows: skip, speed and the position bar only for an item
    /// that is not live.
    func configure(for state: PlaybackState) {
        let hasItem = state.current != nil
        let seekable = hasItem && !state.isLive
        center.playCommand.isEnabled = hasItem
        center.pauseCommand.isEnabled = hasItem
        center.togglePlayPauseCommand.isEnabled = hasItem
        center.skipBackwardCommand.isEnabled = seekable
        center.skipForwardCommand.isEnabled = seekable
        center.changePlaybackRateCommand.isEnabled = seekable
        center.changePlaybackPositionCommand.isEnabled = seekable && state.durationMs > 0
        for command in Self.unsupported(center) {
            command.isEnabled = false
        }
    }

    /// Never enabled: next/previous track (they would replace the skip buttons) and seek
    /// forward/backward.
    static func unsupported(_ center: MPRemoteCommandCenter) -> [MPRemoteCommand] {
        [
            center.nextTrackCommand,
            center.previousTrackCommand,
            center.seekForwardCommand,
            center.seekBackwardCommand,
        ]
    }

    // MARK: - Handlers (internal for unit tests: `MPRemoteCommandEvent` cannot be created)

    func handlePlay() -> MPRemoteCommandHandlerStatus {
        guard let handler else { return .noActionableNowPlayingItem }
        handler.play()
        return .success
    }

    func handlePause() -> MPRemoteCommandHandlerStatus {
        guard let handler else { return .noActionableNowPlayingItem }
        handler.pause()
        return .success
    }

    func handleTogglePlayPause() -> MPRemoteCommandHandlerStatus {
        guard let handler else { return .noActionableNowPlayingItem }
        handler.togglePlayPause()
        return .success
    }

    func handleSkip(seconds: Double) -> MPRemoteCommandHandlerStatus {
        guard let handler else { return .noActionableNowPlayingItem }
        guard seconds.isFinite else { return .commandFailed }
        handler.skip(bySeconds: seconds)
        return .success
    }

    func handleChangeRate(_ rate: Float) -> MPRemoteCommandHandlerStatus {
        guard let handler else { return .noActionableNowPlayingItem }
        guard rate.isFinite, rate > 0 else { return .commandFailed }
        handler.changeRate(rate)
        return .success
    }

    func handleChangePosition(seconds: TimeInterval) -> MPRemoteCommandHandlerStatus {
        guard let handler else { return .noActionableNowPlayingItem }
        guard seconds.isFinite, seconds >= 0 else { return .commandFailed }
        handler.seek(toMs: Int64(seconds * 1000))
        return .success
    }

    private func add(_ command: MPRemoteCommand, _ action: @escaping @MainActor (RemoteCommands) -> MPRemoteCommandHandlerStatus) {
        targets.append((command, command.addTarget { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self else { return .commandFailed }
                return action(self)
            }
        }))
    }
}
