import SwiftUI

/// One episode of Podcast detail (Android `EpisodeWidget`): 60 pt artwork with the episode's
/// `Referer`, the title (no line limit), a "date · duration" line and the plain-text
/// description on at most 3 lines.
struct EpisodeRowView: View {
    let row: EpisodeRow
    let index: Int
    /// The player holds this episode: a `waveform` mark before the title (M3 §3.4).
    var isNowPlaying = false

    @Environment(\.locale) private var locale

    var body: some View {
        let meta = row.meta(locale: locale)
        HStack(alignment: .top, spacing: 12) {
            ArtworkImage(url: row.imageURL, referer: row.referer, cornerRadius: 12)
                .frame(width: 60, height: 60)
            VStack(alignment: .leading, spacing: 4) {
                if isNowPlaying {
                    Label("player.nowPlaying", systemImage: "waveform")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.brandPrimary)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(Text("player.nowPlaying"))
                        .accessibilityIdentifier("detail.episode.\(index).nowPlaying")
                }
                Text(verbatim: row.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .accessibilityIdentifier("detail.episode.\(index).title")
                if !meta.isEmpty {
                    Text(verbatim: meta)
                        .font(.caption)
                        .foregroundStyle(Color.brandSecondary)
                        .accessibilityIdentifier("detail.episode.\(index).meta")
                }
                if !row.summary.isEmpty {
                    Text(verbatim: row.summary)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .multilineTextAlignment(.leading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

/// Shimmer placeholder with the shape of `EpisodeRowView`.
struct EpisodeRowPlaceholder: View {
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(.quaternary)
                .frame(width: 60, height: 60)
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: "Episode title placeholder")
                    .font(.headline)
                Text(verbatim: "30 Oct 2026 · 45 min")
                    .font(.caption)
                Text(verbatim: "Episode description placeholder on two lines of text")
                    .font(.subheadline)
                    .lineLimit(2, reservesSpace: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

#Preview {
    VStack(spacing: 0) {
        EpisodeRowView(
            row: EpisodeRow(
                id: 1,
                title: "Tập 1: Mở đầu",
                summary: "Mô tả của tập, có thể dài tới ba dòng trước khi bị cắt bớt bằng dấu ba chấm ở cuối.",
                imageURL: nil,
                referer: nil,
                publishedAt: .now,
                durationSeconds: 3900,
                enclosureURL: "",
                enclosureType: "audio/mpeg"
            ),
            index: 0
        )
        EpisodeRowPlaceholder().shimmer()
    }
}
