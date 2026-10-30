import AVFoundation
import Observation
@preconcurrency import Shared

/// The player of the app (audio-playback-ios §3), Android's `PlayerManager` + `PlayerViewModel`:
/// **one** `AVPlayer` and a self-managed queue (shared `PlaybackQueue`). Like
/// `setPauseAtEndOfMediaItems(true)`, it pauses at the end of every episode and never moves on
/// by itself. It publishes `state` (Android `PlayerControlState`), keeps the listening position
/// (shared `PlaybackPositions`, [Sửa] features-spec §3.6), records `extra:current_item_id`
/// (shared `PlaybackHistory`), keeps the favourite flag in Room (shared `FavouriteRepository`)
/// and feeds Now Playing and the remote commands.
@MainActor
@Observable
final class AudioEngine: PlaybackController, RemoteCommandHandling, AudioSessionEventHandling {
    /// Debug-only XCUITest hooks (M3 §4.6); `.none` in Release.
    struct Hooks: Equatable, Sendable {
        /// Played instead of every item's URL (`TSP_UITEST_AUDIO_URL`).
        var audioURLOverride: URL?
        /// Every item counts as live (`-uitest-force-live`).
        var forceLive = false

        static let none = Hooks()
    }

    private(set) var state = PlaybackState()

    @ObservationIgnored private var player: AVPlayer
    @ObservationIgnored private var queue: PlaybackQueue?
    @ObservationIgnored private var items: [PlaybackItem] = []
    /// What the user asked for (Android `playWhenReady`): true while playing or buffering.
    @ObservationIgnored private var wantsToPlay = false
    @ObservationIgnored private var wasPlayingBeforeInterruption = false
    @ObservationIgnored private var reachedEnd = false
    @ObservationIgnored private var failed = false
    /// The loaded stream has no duration.
    @ObservationIgnored private var detectedLive = false
    /// Resume position applied once the item is ready (seeking earlier throws).
    @ObservationIgnored private var pendingStartMs: Int64 = 0
    /// The slider moved while the resume seek was in flight: `pendingStartMs` holds the new target.
    @ObservationIgnored private var seekRequestedDuringStart = false
    @ObservationIgnored private var lastSavedPositionMs: Int64 = 0
    @ObservationIgnored private var loadGeneration = 0
    /// Position, history and favourite reads/writes run one after the other, so a read never
    /// overtakes a write.
    @ObservationIgnored private var persistence: Task<Void, Never>?
    @ObservationIgnored private var timeObserver: Any?
    @ObservationIgnored private var playerObservation: NSKeyValueObservation?
    @ObservationIgnored private var itemObservation: NSKeyValueObservation?
    @ObservationIgnored private var itemTokens: [any NSObjectProtocol] = []

    @ObservationIgnored private let positions: PlaybackPositions
    @ObservationIgnored private let history: PlaybackHistory
    @ObservationIgnored private let favourites: FavouriteRepository
    @ObservationIgnored private let session: AudioSessionController
    @ObservationIgnored private let nowPlaying: NowPlayingCenter
    @ObservationIgnored private let remoteCommands: RemoteCommands
    @ObservationIgnored let hooks: Hooks
    #if DEBUG
    /// Unit-test seam: runs right after the resume seek is sent, while it is still in flight.
    @ObservationIgnored var onStartSeekSent: (() -> Void)?
    #endif

    init(
        positions: PlaybackPositions,
        history: PlaybackHistory,
        favourites: FavouriteRepository,
        session: AudioSessionController = AudioSessionController(),
        nowPlaying: NowPlayingCenter = NowPlayingCenter(),
        remoteCommands: RemoteCommands = RemoteCommands(),
        hooks: Hooks = .none
    ) {
        self.positions = positions
        self.history = history
        self.favourites = favourites
        self.session = session
        self.nowPlaying = nowPlaying
        self.remoteCommands = remoteCommands
        self.hooks = hooks
        player = Self.makePlayer()
        attachPlayerObservers()
        session.configure(handler: self)
        remoteCommands.install(handler: self)
    }

    /// The one place that creates an `AVPlayer` (also after a media services reset).
    private static func makePlayer() -> AVPlayer {
        let player = AVPlayer()
        player.actionAtItemEnd = .pause
        player.automaticallyWaitsToMinimizeStalling = true
        return player
    }

    /// Identity of the current `AVPlayer` (unit tests check the reset path).
    var playerIdentity: ObjectIdentifier { ObjectIdentifier(player) }

    // MARK: - PlaybackController

    func play(_ request: PlaybackRequest) {
        let shared = request.items.map { $0.shared() }
        guard let queue = PlaybackQueue.companion.of(items: shared, startIndex: Int32(clamping: request.startIndex)) else { return }
        savePosition()
        items = request.items
        self.queue = queue
        let item = items[Int(queue.index)]
        AppLog.playback.notice(
            "play feedId=\(item.feedId, privacy: .public) id=\(item.id, privacy: .public) index=\(queue.index, privacy: .public) count=\(request.items.count, privacy: .public)"
        )
        load(autoplay: true)
    }

    // MARK: - Controls (Player, MiniPlayer, remote commands)

    func play() {
        guard state.current != nil else { return }
        AppLog.playback.notice("command play")
        if failed {
            retry()
            return
        }
        wasPlayingBeforeInterruption = false
        if reachedEnd {
            reachedEnd = false
            player.seek(to: .zero)
        }
        wantsToPlay = true
        if player.currentItem?.status == .readyToPlay, pendingStartMs == 0 {
            session.activate()
            player.play()
        }
        publish()
        refreshSystemControls()
    }

    func pause() {
        AppLog.playback.notice("command pause")
        wasPlayingBeforeInterruption = false
        pausePlayback()
    }

    func togglePlayPause() {
        if wantsToPlay, !failed {
            pause()
        } else {
            play()
        }
    }

    /// Slider: `fraction` of the duration. Ignored for live streams or an unknown duration.
    func seek(toFraction fraction: Double) {
        guard !state.isLive, state.durationMs > 0, fraction.isFinite else { return }
        seek(toMs: Int64((min(max(fraction, 0), 1) * Double(state.durationMs)).rounded()))
    }

    func seek(toMs positionMs: Int64) {
        guard state.current != nil, !state.isLive else { return }
        let duration = state.durationMs
        let target = max(0, duration > 0 ? min(positionMs, duration) : positionMs)
        reachedEnd = false
        guard player.currentItem?.status == .readyToPlay, pendingStartMs == 0 else {
            pendingStartMs = target
            seekRequestedDuringStart = true
            publish()
            return
        }
        state.positionMs = target
        player.seek(to: CMTime(value: target, timescale: 1000), toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
            Task { @MainActor in
                self?.publish()
                self?.refreshSystemControls()
            }
        }
        refreshSystemControls()
    }

    /// Next item of the queue; nothing at the end (Android `hasNextMediaItem()`). Keeps playing
    /// or paused like ExoPlayer's `playWhenReady`.
    func skipNext() {
        guard let next = queue?.next() else { return }
        move(to: next, autoplay: wantsToPlay)
    }

    func skipPrevious() {
        guard let previous = queue?.previous() else { return }
        move(to: previous, autoplay: wantsToPlay)
    }

    /// Up next row `index` (Android `playerMediaItem`): plays it within the same queue.
    func select(index: Int) {
        guard let selected = queue?.select(newIndex: Int32(clamping: index)) else { return }
        move(to: selected, autoplay: true)
    }

    /// "Tải lại" after an error: the same item from where it was.
    func retry() {
        guard queue != nil else { return }
        load(autoplay: true, startAtMs: state.positionMs)
    }

    /// Android `onFavouriteChanged`: flips the row in Room, then shows the stored value. Queued
    /// behind the read of the current item, so a quick tap never acts on a stale flag.
    func toggleFavourite() {
        guard let item = state.current else { return }
        let favourites = favourites
        let favourite = item.favourite
        let generation = loadGeneration
        enqueuePersistence { [weak self] in
            do {
                let isFavourite = try await favourites.toggle(item: favourite).boolValue
                guard let self, generation == self.loadGeneration else { return }
                self.state.isFavourite = isFavourite
            } catch {
                AppLog.playback.error("favourite_save_failed")
            }
        }
    }

    /// App going to the background (M3 §4.3).
    func persistPosition() {
        savePosition()
    }

    /// Stops and forgets everything (unit tests).
    func shutdown() {
        wantsToPlay = false
        player.pause()
        detachItemObservers()
        player.replaceCurrentItem(with: nil)
        remoteCommands.uninstall()
        nowPlaying.clear()
    }

    // MARK: - AudioSessionEventHandling

    func audioSessionInterruptionBegan() {
        let wasPlaying = wantsToPlay
        pausePlayback()
        wasPlayingBeforeInterruption = wasPlaying
    }

    func audioSessionInterruptionEnded(shouldResume: Bool) {
        let resume = shouldResume && wasPlayingBeforeInterruption
        wasPlayingBeforeInterruption = false
        if resume {
            play()
        }
    }

    func audioSessionOldDeviceUnavailable() {
        pause()
    }

    func audioSessionMediaServicesWereReset() {
        let position = state.positionMs
        detachItemObservers()
        detachPlayerObservers()
        player = Self.makePlayer()
        attachPlayerObservers()
        remoteCommands.install(handler: self)
        guard queue != nil else {
            publish()
            return
        }
        load(autoplay: false, startAtMs: position)
    }

    // MARK: - Loading

    private func move(to newQueue: PlaybackQueue, autoplay: Bool) {
        savePosition()
        queue = newQueue
        load(autoplay: autoplay)
    }

    /// Loads the queue's current item. `startAtMs` nil: resume from the saved position.
    private func load(autoplay: Bool, startAtMs: Int64? = nil) {
        guard let queue else { return }
        loadGeneration += 1
        let generation = loadGeneration
        let index = Int(queue.index)
        let item = items[index]

        detachItemObservers()
        player.pause()
        player.replaceCurrentItem(with: nil)
        failed = false
        reachedEnd = false
        detectedLive = false
        wantsToPlay = autoplay
        pendingStartMs = 0
        seekRequestedDuringStart = false
        lastSavedPositionMs = startAtMs ?? 0

        var next = PlaybackState()
        next.current = item
        next.queue = items
        next.index = index
        next.isLive = hooks.forceLive || item.isLive
        next.durationMs = next.isLive ? 0 : item.durationMs
        next.positionMs = startAtMs ?? 0
        next.isFavourite = false
        next.isBuffering = autoplay
        state = next
        refreshSystemControls()

        let shared = item.shared(isLive: next.isLive)
        let history = history
        let positions = positions
        let favourites = favourites
        enqueuePersistence { [weak self] in
            let isFavourite = (try? await favourites.isFavourite(type: .podcastepisode, id: shared.id))?.boolValue ?? false
            guard let self, generation == self.loadGeneration else { return }
            self.state.isFavourite = isFavourite
        }
        enqueuePersistence {
            do {
                try await history.saveCurrentId(itemId: shared.id)
            } catch {
                AppLog.playback.error("history_save_failed")
            }
        }
        enqueuePersistence { [weak self] in
            var start = startAtMs ?? 0
            if startAtMs == nil, !shared.isLive {
                start = (try? await positions.resumePositionMs(item: shared))?.int64Value ?? 0
            }
            guard let self, generation == self.loadGeneration else { return }
            self.startItem(item, startMs: start)
        }
    }

    private func startItem(_ item: PlaybackItem, startMs: Int64) {
        guard let url = hooks.audioURLOverride ?? Self.url(item.url) else {
            AppLog.playback.error("playback_failed id=\(item.id, privacy: .public) reason=invalid_url")
            fail()
            return
        }
        // No custom User-Agent or headers for audio hosts (M3 §7 item 9).
        let playerItem = AVPlayerItem(url: url)
        attachItemObservers(playerItem)
        pendingStartMs = startMs
        lastSavedPositionMs = startMs
        player.replaceCurrentItem(with: playerItem)
        publish()
    }

    private static func url(_ raw: String) -> URL? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed), let scheme = url.scheme?.lowercased(), ["http", "https", "file"].contains(scheme) else {
            return nil
        }
        return url
    }

    private func itemStatusChanged(_ status: AVPlayerItem.Status) {
        switch status {
        case .readyToPlay:
            detectedLive = PlaybackState.isIndefinite(player.currentItem?.duration ?? .invalid)
            seekRequestedDuringStart = false
            applyStartPosition()
        case .failed:
            AppLog.playback.error("playback_failed id=\(self.state.current?.id ?? "-", privacy: .public) reason=item_failed")
            fail()
        case .unknown:
            publish()
        @unknown default:
            publish()
        }
    }

    /// Seeks to `pendingStartMs` once the item is ready, then starts if wanted. A slider seek made
    /// while that seek is in flight replaces the target instead of being dropped (M3 review round 1).
    private func applyStartPosition() {
        let start = pendingStartMs
        guard start > 0, !(hooks.forceLive || detectedLive || (state.current?.isLive ?? false)) else {
            pendingStartMs = 0
            startIfWanted()
            return
        }
        let generation = loadGeneration
        player.seek(to: CMTime(value: start, timescale: 1000), toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
            Task { @MainActor in
                guard let self, generation == self.loadGeneration else { return }
                self.startSeekFinished(start: start)
            }
        }
        #if DEBUG
        onStartSeekSent?()
        #endif
    }

    private func startSeekFinished(start: Int64) {
        let moved = seekRequestedDuringStart && pendingStartMs != start
        seekRequestedDuringStart = false
        if moved, pendingStartMs > 0 {
            applyStartPosition()
            return
        }
        if moved {
            player.seek(to: .zero, toleranceBefore: .zero, toleranceAfter: .zero)
        }
        pendingStartMs = 0
        startIfWanted()
    }

    private func startIfWanted() {
        if wantsToPlay {
            session.activate()
            player.play()
        }
        publish()
        refreshSystemControls()
    }

    private func itemDidPlayToEnd() {
        // `actionAtItemEnd = .pause`: stay on this item, like `setPauseAtEndOfMediaItems(true)`.
        wantsToPlay = false
        reachedEnd = true
        player.pause()
        publish()
        savePosition()
        refreshSystemControls()
    }

    private func fail() {
        failed = true
        wantsToPlay = false
        player.pause()
        publish()
        refreshSystemControls()
    }

    private func pausePlayback() {
        guard state.current != nil else { return }
        wantsToPlay = false
        player.pause()
        publish()
        savePosition()
        refreshSystemControls()
    }

    // MARK: - State

    private func tick() {
        publish()
        if state.isPlaying, !state.isLive,
           abs(state.positionMs - lastSavedPositionMs) >= PlaybackPositions.companion.SAVE_INTERVAL_MS {
            savePosition()
        }
    }

    private func publish() {
        guard state.current != nil else { return }
        let item = player.currentItem
        var next = state
        next.isPlaying = !failed && player.timeControlStatus == .playing
        next.isBuffering = PlaybackState.isBuffering(
            timeControlStatus: player.timeControlStatus,
            itemStatus: item?.status,
            wantsToPlay: wantsToPlay,
            failed: failed
        )
        next.isLive = hooks.forceLive || (state.current?.isLive ?? false) || detectedLive
        if next.isLive {
            next.durationMs = 0
        } else if let item, item.status == .readyToPlay {
            let duration = PlaybackState.milliseconds(item.duration)
            next.durationMs = duration > 0 ? duration : (state.current?.durationMs ?? 0)
        }
        if pendingStartMs > 0 {
            next.positionMs = pendingStartMs
        } else if item != nil {
            next.positionMs = PlaybackState.milliseconds(player.currentTime())
        }
        next.error = failed ? .failed : nil
        if next != state {
            state = next
        }
    }

    private func refreshSystemControls() {
        nowPlaying.update(state)
        remoteCommands.configure(for: state)
    }

    // MARK: - Persistence

    private func savePosition() {
        guard let item = state.current, !state.isLive, !failed else { return }
        let position = state.positionMs
        let duration = state.durationMs
        guard position != lastSavedPositionMs || reachedEnd else { return }
        lastSavedPositionMs = position
        let shared = item.shared(isLive: false)
        let positions = positions
        enqueuePersistence {
            do {
                try await positions.record(item: shared, positionMs: position, durationMs: duration)
            } catch {
                AppLog.playback.error("position_save_failed")
            }
        }
    }

    private func enqueuePersistence(_ operation: @escaping @MainActor () async -> Void) {
        let previous = persistence
        persistence = Task { @MainActor in
            await previous?.value
            await operation()
        }
    }

    /// Waits for every queued position/history write (unit tests).
    func waitForPersistence() async {
        await persistence?.value
    }

    // MARK: - Observers

    private func attachPlayerObservers() {
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(value: 1, timescale: 4), queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.tick() }
        }
        playerObservation = player.observe(\.timeControlStatus, options: [.new]) { [weak self] _, _ in
            Task { @MainActor in
                self?.publish()
                self?.refreshSystemControls()
            }
        }
    }

    private func detachPlayerObservers() {
        if let timeObserver {
            player.removeTimeObserver(timeObserver)
        }
        timeObserver = nil
        playerObservation?.invalidate()
        playerObservation = nil
    }

    private func attachItemObservers(_ item: AVPlayerItem) {
        let id = ObjectIdentifier(item)
        itemObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            let status = item.status
            Task { @MainActor in
                guard let self, self.player.currentItem.map(ObjectIdentifier.init) == id else { return }
                self.itemStatusChanged(status)
            }
        }
        let center = NotificationCenter.default
        itemTokens = [
            center.addObserver(forName: AVPlayerItem.didPlayToEndTimeNotification, object: item, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated { self?.itemDidPlayToEnd() }
            },
            center.addObserver(forName: AVPlayerItem.failedToPlayToEndTimeNotification, object: item, queue: .main) { [weak self] _ in
                MainActor.assumeIsolated {
                    AppLog.playback.error("playback_failed reason=failed_to_play_to_end")
                    self?.fail()
                }
            },
        ]
    }

    private func detachItemObservers() {
        itemObservation?.invalidate()
        itemObservation = nil
        for token in itemTokens {
            NotificationCenter.default.removeObserver(token)
        }
        itemTokens = []
    }
}
