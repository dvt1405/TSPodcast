import SwiftUI
@preconcurrency import Shared

/// The MiniPlayer (Android `PlayerWidgetMain`): artwork, title, podcast name, play/pause and a
/// thin progress line. A tap on the artwork or the titles opens the Player.
struct MiniPlayerView: View {
    let engine: AudioEngine
    let open: () -> Void

    var body: some View {
        let state = engine.state
        if let item = state.current {
            let showsPause = state.isPlaying || state.isBuffering
            HStack(spacing: 12) {
                // Two sibling buttons: a container tap gesture would swallow the play/pause tap.
                Button(action: open) {
                    HStack(spacing: 12) {
                        ArtworkImage(url: item.artworkURL, referer: item.referer, cornerRadius: 8)
                            .frame(width: 36, height: 36)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(verbatim: item.title)
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(1)
                            Text(verbatim: item.podcastTitle)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
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
            .padding(.vertical, 4)
            .frame(maxWidth: .infinity)
            .overlay(alignment: .bottom) {
                if !state.isLive {
                    ProgressView(value: state.progress)
                        .progressViewStyle(.linear)
                        .tint(Color.brandPrimary)
                        .scaleEffect(x: 1, y: 0.5, anchor: .bottom)
                        .padding(.horizontal, 16)
                        .accessibilityIdentifier("miniplayer.progress")
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("miniplayer.slot")
        }
    }
}
