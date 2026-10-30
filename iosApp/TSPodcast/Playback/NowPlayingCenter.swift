import MediaPlayer
import UIKit

/// Lock Screen / Control Center / Dynamic Island metadata (audio-playback-ios §5.1), Android's
/// `TSNotificationProvider`. Updated when the item, play/pause, a seek or the speed changes, never
/// every tick: the system interpolates the elapsed time from the rate.
@MainActor
final class NowPlayingCenter {
    private let center: MPNowPlayingInfoCenter
    private let artworkLoader: ArtworkLoader
    private var artworkItemID: String?
    private var artwork: MPMediaItemArtwork?

    init(center: MPNowPlayingInfoCenter = .default(), artworkLoader: ArtworkLoader = .shared) {
        self.center = center
        self.artworkLoader = artworkLoader
    }

    func update(_ state: PlaybackState) {
        guard let item = state.current else {
            clear()
            return
        }
        var info = Self.info(for: state)
        if artworkItemID == item.id, let artwork {
            info[MPMediaItemPropertyArtwork] = artwork
        } else if artworkItemID != item.id {
            loadArtwork(for: item)
        }
        center.nowPlayingInfo = info
    }

    func clear() {
        artworkItemID = nil
        artwork = nil
        center.nowPlayingInfo = nil
    }

    /// The dictionary without artwork (internal for unit tests).
    static func info(for state: PlaybackState) -> [String: Any] {
        guard let item = state.current else { return [:] }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: item.title,
            MPMediaItemPropertyArtist: item.podcastTitle,
            MPMediaItemPropertyAlbumTitle: item.podcastTitle,
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.audio.rawValue,
            MPNowPlayingInfoPropertyIsLiveStream: state.isLive,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: Double(state.positionMs) / 1000,
            // The system interpolates the elapsed time with this rate (1.5× runs 1.5 s per second).
            MPNowPlayingInfoPropertyPlaybackRate: state.isPlaying ? state.rate : 0.0,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: state.rate,
        ]
        if !state.isLive, state.durationMs > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = Double(state.durationMs) / 1000
        }
        if state.queue.count > 1 {
            info[MPNowPlayingInfoPropertyPlaybackQueueIndex] = state.index
            info[MPNowPlayingInfoPropertyPlaybackQueueCount] = state.queue.count
        }
        return info
    }

    /// MediaPlayer calls the image handler on its own queue: the closure must not be
    /// MainActor-isolated (a MainActor closure traps there), hence `nonisolated` + `@Sendable`.
    nonisolated static func artwork(_ image: UIImage) -> MPMediaItemArtwork {
        MPMediaItemArtwork(boundsSize: image.size) { @Sendable _ in image }
    }

    private func loadArtwork(for item: PlaybackItem) {
        artworkItemID = item.id
        artwork = nil
        let loader = artworkLoader
        Task { [weak self] in
            guard let image = await loader.image(for: item.artworkURL, referer: item.referer) else { return }
            guard let self, self.artworkItemID == item.id else { return }
            let artwork = Self.artwork(image)
            self.artwork = artwork
            var info = self.center.nowPlayingInfo ?? [:]
            info[MPMediaItemPropertyArtwork] = artwork
            self.center.nowPlayingInfo = info
        }
    }
}
