import SwiftUI
@preconcurrency import Shared

/// "Thời sự & Bản tin" on Home, right after trending, per podcast location (Android
/// `LocationNewsSection`): chips for the locations that have bulletins (none when only one has),
/// then that location's newest bulletins. Starts on the primary location.
///
/// For the primary location the curated hub (M6 §3.4) replaces the catalog list when it has news:
/// the morning brief card (hidden when no bulletin is in its window), a row of the newest episodes
/// with each feed's own cover, and "Xem tất cả" to the hub. Other locations, or the primary one
/// without the hub, show the catalog's bulletins (newest episode of each trending News show); a tap
/// plays that row from the item and opens the Player. Loading, error and "Tải lại" of the hub follow
/// the other sections (`home.section.news.loading|error|retry`) while no location has news yet;
/// with nothing at all there is no section, not even a header.
struct HomeNewsSection: View {
    let model: HomeModel
    let retry: () -> Void

    @Environment(AppEnvironment.self) private var environment
    @State private var selectedRegion: String?

    /// Episodes of the Home row; the hub lists all of "Mới nhất".
    static let rowLimit = 10

    var body: some View {
        let regions = model.newsRegions()
        if let selected = regions.first(where: { $0.region == selectedRegion }) ?? regions.first {
            let hub = selected.region == model.locations.first?.region ? model.news.content : nil
            VStack(alignment: .leading, spacing: 0) {
                header(showsSeeAll: hub != nil, hasChips: regions.count > 1)
                if regions.count > 1 {
                    LocationChips(locations: regions, selectedRegion: selected.region, identifierPrefix: "home.news.chip") { region in
                        trackChip(region)
                        withAnimation(.easeInOut(duration: 0.2)) { selectedRegion = region }
                    }
                    .padding(.bottom, 8)
                }
                Group {
                    if let hub {
                        hubContent(hub)
                    } else {
                        catalogRow(model.locationNews[selected.region] ?? [])
                    }
                }
                .id(selected.region)
                .transition(.opacity)
            }
            .onChange(of: regions.map(\.region)) { selectedRegion = nil }
        } else {
            hubOnly
        }
    }

    /// No location has news (yet): the hub's own states, as before locations existed.
    @ViewBuilder
    private var hubOnly: some View {
        switch model.news {
        case .absent:
            EmptyView()
        case .loading:
            VStack(alignment: .leading, spacing: 0) {
                header(showsSeeAll: false, hasChips: false)
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
                header(showsSeeAll: false, hasChips: false)
                SectionMessage(section: .news, isFailure: true, retry: retry)
            }
        case .loaded(let content):
            VStack(alignment: .leading, spacing: 0) {
                header(showsSeeAll: true, hasChips: false)
                hubContent(content)
            }
        }
    }

    @ViewBuilder
    private func hubContent(_ content: NewsHubContent) -> some View {
        if !content.brief.isEmpty {
            BriefCard(content: content, identifier: "home.news.brief", openRoute: .newsHub(.brief)) {
                environment.newsHubActions.playBrief(content, screen: HomeView.analyticsScreen)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
        }
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

    /// A location's bulletins from the catalog: plays the row from the tapped item (auto-advance
    /// off, like "Mới nhất") and opens the Player.
    private func catalogRow(_ items: [NewsItem]) -> some View {
        HorizontalRow {
            ForEach(Array(items.prefix(Self.rowLimit).enumerated()), id: \.offset) { index, item in
                Button {
                    environment.play(PlaybackRequest(news: Array(items.prefix(Self.rowLimit)), startIndex: index))
                } label: {
                    NewsEpisodeCard(item: item)
                }
                .buttonStyle(PressableStyle())
                .hideShowContextMenu(feedId: item.feedId, title: item.feedTitle, imageURL: item.imageURL, screen: HomeView.analyticsScreen)
                .accessibilityLabel(Text(verbatim: item.title))
                .accessibilityValue(Text(verbatim: item.feedTitle))
                .accessibilityIdentifier("home.news.location.item.\(index)")
            }
        }
    }

    private func header(showsSeeAll: Bool, hasChips: Bool) -> some View {
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
        .padding(.bottom, showsSeeAll || hasChips ? 4 : 12)
    }

    private func trackChip(_ region: String) {
        let events = AnalyticsEvents.shared
        environment.analytics.trackEvent(
            name: events.LOCATION_CHIP_SELECT,
            screen: HomeView.analyticsScreen,
            params: [events.PARAM_REGION: region, events.PARAM_SECTION: events.SECTION_NEWS]
        )
    }
}
