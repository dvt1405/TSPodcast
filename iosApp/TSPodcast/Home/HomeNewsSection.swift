import SwiftUI

/// "Thời sự & Bản tin" on Home, right after trending (M6 §3.4): the morning brief card (hidden
/// when no bulletin is in its window), a row of the newest episodes with each feed's own cover,
/// and "Xem tất cả" to the hub. Loading, error and "Tải lại" follow the other sections
/// (`home.section.news.loading|error|retry`). While the hub is off or empty there is no section
/// at all, not even a header.
struct HomeNewsSection: View {
    let state: NewsSectionState
    let retry: () -> Void

    @Environment(AppEnvironment.self) private var environment

    /// Episodes of the Home row; the hub lists all of "Mới nhất".
    static let rowLimit = 10

    var body: some View {
        switch state {
        case .absent:
            EmptyView()
        case .loading:
            VStack(alignment: .leading, spacing: 0) {
                header(showsSeeAll: false)
                HorizontalRow(isScrollEnabled: false) {
                    ForEach(0..<3, id: \.self) { _ in
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(.quaternary)
                            .frame(width: NewsEpisodeCard.width, height: NewsEpisodeCard.width + 60)
                    }
                }
                .shimmer()
                .sectionLoading(.news)
            }
        case .failed:
            VStack(alignment: .leading, spacing: 0) {
                header(showsSeeAll: false)
                SectionMessage(section: .news, isFailure: true, retry: retry)
            }
        case .loaded(let content):
            VStack(alignment: .leading, spacing: 0) {
                header(showsSeeAll: true)
                if !content.brief.isEmpty {
                    BriefCard(content: content, identifier: "home.news.brief", openRoute: .newsHub(.brief)) {
                        environment.newsHubActions.playBrief(content, screen: HomeView.analyticsScreen)
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
                latestRow(content)
            }
        }
    }

    private func header(showsSeeAll: Bool) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text("news.title")
                .font(.title3.weight(.bold))
                .foregroundStyle(Color.textTitle)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("home.section.news")
            Spacer(minLength: 8)
            if showsSeeAll {
                NavigationLink(value: DiscoverRoute.newsHub(.seeAll)) {
                    Text("news.seeAll")
                        .font(.subheadline.weight(.semibold))
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityIdentifier("home.news.seeAll")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.top, 20)
        .padding(.bottom, showsSeeAll ? 4 : 12)
    }

    private func latestRow(_ content: NewsHubContent) -> some View {
        HorizontalRow {
            ForEach(Array(content.latest.prefix(Self.rowLimit).enumerated()), id: \.offset) { index, item in
                Button {
                    environment.newsHubActions.playLatest(content, at: index)
                } label: {
                    NewsEpisodeCard(item: item)
                }
                .buttonStyle(PressableStyle())
                .hideShowContextMenu(feedId: item.feedId, title: item.feedTitle, imageURL: item.imageURL, screen: HomeView.analyticsScreen)
                .accessibilityLabel(Text(verbatim: item.title))
                .accessibilityValue(Text(verbatim: item.feedTitle))
                .accessibilityIdentifier("home.news.item.\(index)")
            }
        }
    }
}
