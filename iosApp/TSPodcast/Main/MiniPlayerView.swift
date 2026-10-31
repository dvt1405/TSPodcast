import SwiftUI
@preconcurrency import Shared

/// The MiniPlayer (Android `PlayerWidgetMain`): artwork, title, podcast name, play/pause and a
/// thin progress line. A tap on the artwork or the titles opens the Player.
///
/// `.compact` (iOS 26 inline accessory, or an accessibility text size; `MiniPlayerLayout`) keeps
/// a smaller artwork, the title and play/pause, and hides the podcast name and the progress line.
/// `miniplayer.slot`, `miniplayer.open` and `miniplayer.playPause` exist in both layouts;
/// `miniplayer.progress` only in `.regular` (and, as in M3, not for a live stream). The surface
/// behind it comes from the slot (`MiniPlayerSlot`), not from this view.
struct MiniPlayerView: View {
    let engine: AudioEngine
    var layout: MiniPlayerLayout = .regular
    let open: () -> Void

    var body: some View {
        let state = engine.state
        if let item = state.current {
            let showsPause = state.isPlaying || state.isBuffering
            let isCompact = layout == .compact
            HStack(spacing: isCompact ? 8 : 12) {
                // Two sibling buttons: a container tap gesture would swallow the play/pause tap.
                Button(action: open) {
                    HStack(spacing: isCompact ? 8 : 12) {
                        let side: CGFloat = isCompact ? 28 : 36
                        ArtworkImage(url: item.artworkURL, referer: item.referer, cornerRadius: isCompact ? 6 : 8)
                            .frame(width: side, height: side)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(verbatim: item.title)
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(1)
                            if !isCompact {
                                Text(verbatim: item.podcastTitle)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                // VoiceOver keeps the podcast name even when the compact layout hides it.
                .accessibilityLabel(Text(verbatim: "\(item.title), \(item.podcastTitle)"))
                .accessibilityHint(Text("player.open"))
                .accessibilityIdentifier("miniplayer.open")
                Button {
                    engine.togglePlayPause()
                } label: {
                    Image(systemName: showsPause ? "pause.fill" : "play.fill")
                        .font(.title3)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(showsPause ? "player.pause" : "player.play"))
                .accessibilityIdentifier("miniplayer.playPause")
            }
            .padding(.leading, 12)
            .padding(.trailing, 4)
            .padding(.vertical, isCompact ? 0 : 4)
            .frame(maxWidth: .infinity)
            .overlay(alignment: .bottom) {
                if !isCompact && !state.isLive {
                    // Inset from the capsule's rounded ends (iOS 26 accessory, iOS 17/18 capsule).
                    MiniPlayerProgress(engine: engine)
                        .padding(.horizontal, 22)
                        .padding(.bottom, 3)
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("miniplayer.slot")
        }
    }
}

/// The thin progress line, the only part of the MiniPlayer that follows the 4 Hz ticks
/// (`engine.position`).
private struct MiniPlayerProgress: View {
    let engine: AudioEngine

    var body: some View {
        ProgressView(value: engine.position.progress)
            .progressViewStyle(.linear)
            .tint(Color.brandPrimary)
            .scaleEffect(x: 1, y: 0.5, anchor: .bottom)
            .accessibilityIdentifier("miniplayer.progress")
    }
}
