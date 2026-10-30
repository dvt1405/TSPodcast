import SwiftUI

/// "Tiếp theo" (Android `SlideArea` "UP NEXT"): the whole queue; the playing row is marked and
/// selected; a tap plays that item in the same queue and closes the sheet (`playerMediaItem`).
struct UpNextSheet: View {
    let engine: AudioEngine
    let close: () -> Void

    var body: some View {
        let state = engine.state
        NavigationStack {
            List {
                ForEach(Array(state.queue.enumerated()), id: \.offset) { index, item in
                    let isCurrent = index == state.index
                    Button {
                        engine.select(index: index)
                        close()
                    } label: {
                        HStack(spacing: 12) {
                            ArtworkImage(url: item.artworkURL, referer: item.referer, cornerRadius: 8)
                                .frame(width: 48, height: 48)
                            VStack(alignment: .leading, spacing: 2) {
                                if isCurrent {
                                    Label("player.nowPlaying", systemImage: "waveform")
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(Color.brandPrimary)
                                }
                                Text(verbatim: item.title)
                                    .font(.subheadline.weight(isCurrent ? .bold : .regular))
                                    .foregroundStyle(.primary)
                                    .lineLimit(2)
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
                    .accessibilityAddTraits(isCurrent ? .isSelected : [])
                    .accessibilityIdentifier("upNext.item.\(index)")
                }
            }
            .listStyle(.plain)
            .accessibilityIdentifier("upNext.root")
            .navigationTitle(Text("player.upNext"))
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }
}
