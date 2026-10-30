import MediaPlayer

/// What remote commands drive (`AudioEngine`; a fake in unit tests).
@MainActor
protocol RemoteCommandHandling: AnyObject {
    func play()
    func pause()
    func togglePlayPause()
    func skipNext()
    func skipPrevious()
    func seek(toMs positionMs: Int64)
}

/// `MPRemoteCommandCenter` (audio-playback-ios §5.2, M3 §4.5): play/pause/toggle, next/previous
/// track within the queue and dragging the Lock Screen bar. Skip ±, rate and seek
/// forward/backward stay disabled: the app has none of those features ([iOS+]).
@MainActor
final class RemoteCommands {
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
        add(center.nextTrackCommand) { $0.handleNextTrack() }
        add(center.previousTrackCommand) { $0.handlePreviousTrack() }
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

    /// Enables what the current state allows.
    func configure(for state: PlaybackState) {
        let hasItem = state.current != nil
        center.playCommand.isEnabled = hasItem
        center.pauseCommand.isEnabled = hasItem
        center.togglePlayPauseCommand.isEnabled = hasItem
        center.nextTrackCommand.isEnabled = state.hasNext
        center.previousTrackCommand.isEnabled = state.hasPrevious
        center.changePlaybackPositionCommand.isEnabled = hasItem && !state.isLive && state.durationMs > 0
        for command in Self.unsupported(center) {
            command.isEnabled = false
        }
    }

    static func unsupported(_ center: MPRemoteCommandCenter) -> [MPRemoteCommand] {
        [
            center.skipForwardCommand,
            center.skipBackwardCommand,
            center.seekForwardCommand,
            center.seekBackwardCommand,
            center.changePlaybackRateCommand,
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

    func handleNextTrack() -> MPRemoteCommandHandlerStatus {
        guard let handler else { return .noActionableNowPlayingItem }
        handler.skipNext()
        return .success
    }

    func handlePreviousTrack() -> MPRemoteCommandHandlerStatus {
        guard let handler else { return .noActionableNowPlayingItem }
        handler.skipPrevious()
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
