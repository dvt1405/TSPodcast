import SwiftUI

/// "Điểm tin sáng · Thứ Năm 29/10", "5 tập · 23 phút" and "Phát tất cả" (M6 §3.4), on Home
/// (`home.news.brief`) and on the hub (`newsHub.brief`). On Home the title part also opens the
/// hub (`openRoute`, `news_hub_open` from `home_brief`); "Phát tất cả" is its own button.
struct BriefCard: View {
    let content: NewsHubContent
    /// `home.news.brief` / `newsHub.brief`; the button is `<identifier>.playAll`.
    let identifier: String
    /// Pushed by a tap on the title part; nil on the hub itself.
    var openRoute: DiscoverRoute?
    let playAll: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let openRoute {
                NavigationLink(value: openRoute) {
                    heading
                }
                .buttonStyle(PressableStyle())
            } else {
                heading
            }
            Button(action: playAll) {
                Label("news.brief.playAll", systemImage: "play.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 36)
            }
            .glassProminentButton(tint: Color.brandPrimary)
            .accessibilityIdentifier("\(identifier).playAll")
        }
        .padding(16)
        .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(identifier)
    }

    private var heading: some View {
        let day = BriefCardFormat.day(.now, locale: BriefCardFormat.appLocale, timeZone: .autoupdatingCurrent)
        return HStack(alignment: .center, spacing: 12) {
            BriefArtworkStack(items: content.brief)
            VStack(alignment: .leading, spacing: 2) {
                Text("news.brief.title \(day)")
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text(verbatim: Self.summary(content))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .multilineTextAlignment(.leading)
            Spacer(minLength: 0)
            if openRoute != nil {
                Image(systemName: "chevron.right")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    /// "5 tập · 23 phút" (minutes rounded up); just the episodes when no duration is known.
    static func summary(_ content: NewsHubContent) -> String {
        let episodes = String(localized: "news.brief.episodes \(content.brief.count)")
        let minutes = BriefCardFormat.minutes(totalSeconds: content.briefDurationSeconds)
        guard minutes > 0 else { return episodes }
        return BriefCardFormat.summary(episodes: episodes, minutes: String(localized: "news.brief.minutes \(minutes)"))
    }
}

/// Up to three feed covers of the brief, overlapping.
private struct BriefArtworkStack: View {
    let items: [NewsItem]

    var body: some View {
        let covers = Array(items.prefix(3))
        ZStack(alignment: .leading) {
            ForEach(Array(covers.enumerated()), id: \.offset) { index, item in
                ArtworkImage(url: item.imageURL, cornerRadius: 10)
                    .frame(width: 48, height: 48)
                    .overlay {
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .strokeBorder(Color(uiColor: .systemBackground), lineWidth: 2)
                    }
                    .offset(x: CGFloat(index) * 14)
                    .zIndex(Double(covers.count - index))
            }
        }
        .frame(width: 48 + CGFloat(max(0, covers.count - 1)) * 14, height: 48, alignment: .leading)
        .accessibilityHidden(true)
    }
}

/// A news episode as a row: feed cover, title, "show · date · duration".
struct NewsEpisodeRow: View {
    let item: NewsItem
    var systemImage: String?

    @Environment(\.locale) private var locale

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ArtworkImage(url: item.imageURL, cornerRadius: 10)
                .frame(width: 56, height: 56)
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: item.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                Text(verbatim: meta)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .multilineTextAlignment(.leading)
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.title3)
                    .foregroundStyle(Color.brandPrimary)
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityHidden(true)
            }
        }
        .contentShape(Rectangle())
    }

    private var meta: String {
        [
            item.feedTitle,
            EpisodeRow.date(item.publishedAt, locale: locale, timeZone: .autoupdatingCurrent),
            EpisodeRow.duration(item.durationSeconds, locale: locale),
        ]
        .compactMap { $0 }
        .joined(separator: " · ")
    }
}

/// A news episode as a card of the Home row: feed cover, two-line title, the show's name.
struct NewsEpisodeCard: View {
    static let width: CGFloat = 150
    let item: NewsItem

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ArtworkImage(url: item.imageURL, cornerRadius: 10)
                .frame(width: Self.width, height: Self.width)
            Text(verbatim: item.title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(2, reservesSpace: true)
                .padding(.top, 8)
            Text(verbatim: item.feedTitle)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .padding(.top, 2)
        }
        .frame(width: Self.width, alignment: .leading)
        .multilineTextAlignment(.leading)
        .contentShape(Rectangle())
    }
}
