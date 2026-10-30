import SwiftUI

/// Home/Discover (Android `DiscoverPodcastsScreen`): trending, music (+ subtitle), live, recent
/// and "Có thể bạn yêu thích", then the Podcast Index attribution required by its terms (7.2).
/// No ad slots and no "Privacy options" link (owner decision D1/D2).
///
/// Podcast taps push Podcast detail (`PodcastRoute`) on this tab's stack, zooming out of the
/// tapped cover on iOS 18+; a Live tap plays the whole Live list from that item and opens the
/// Player (Android `playLive(liveEpisode, list)`).
struct HomeView: View {
    let model: HomeModel

    @Namespace private var zoomNamespace

    var body: some View {
        ScrollView {
            // `home.root` (content of the tab) sits inside `home.list` (the scroll view that
            // carries the Debug value): a wrapper around the scroll view would merge with it.
            // A plain VStack: a top-level LazyVStack underestimated its height after errors and
            // left the footer under the tab bar. Only the long forYou list is lazy.
            VStack(alignment: .leading, spacing: 0) {
                SectionHeader(section: .trending, title: "home.section.trending")
                PodcastCarousel(section: .trending, state: model.trending, zoomNamespace: zoomNamespace, retry: retry(.trending))

                SectionHeader(section: .music, title: "home.section.music", subtitle: "home.section.music.subtitle")
                PodcastCarousel(section: .music, state: model.music, zoomNamespace: zoomNamespace, retry: retry(.music))

                SectionHeader(section: .live, title: "home.section.live")
                LivePager(state: model.live, retry: retry(.live))

                SectionHeader(section: .recent, title: "home.section.recent")
                PodcastCarousel(section: .recent, state: model.recent, zoomNamespace: zoomNamespace, retry: retry(.recent))

                SectionHeader(section: .forYou, title: "home.section.forYou")
                ForYouList(state: model.forYou, zoomNamespace: zoomNamespace, retry: retry(.forYou))

                AttributionFooter()
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("home.root")
        }
        .accessibilityIdentifier("home.list")
        #if DEBUG
        .accessibilityValue("generation=\(model.generation)")
        #endif
        .refreshable { await model.refresh() }
        .task { await model.loadIfNeeded() }
        .navigationDestination(for: PodcastRoute.self) { route in
            PodcastDetailDestination(route: route)
                .zoomTransition(sourceID: route.transitionSourceID, in: zoomNamespace)
        }
    }

    private func retry(_ section: HomeSection) -> () -> Void {
        { [model] in
            Task { await model.retry(section) }
        }
    }
}

// MARK: - Sections

/// Section title (Android `TextStyles.Title4`), id `home.section.<name>`.
private struct SectionHeader: View {
    let section: HomeSection
    let title: LocalizedStringKey
    var subtitle: LocalizedStringKey?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.title3.weight(.bold))
                .foregroundStyle(Color.textTitle)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("home.section.\(section.rawValue)")
            if let subtitle {
                Text(subtitle)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .accessibilityIdentifier("home.section.\(section.rawValue).subtitle")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.top, 20)
        .padding(.bottom, 12)
    }
}

/// Trending, music and recent rows (Android `TrendingRow`/`RecentRow`): 250 pt cards.
private struct PodcastCarousel: View {
    let section: HomeSection
    let state: SectionState<PodcastCard>
    let zoomNamespace: Namespace.ID
    let retry: () -> Void

    var body: some View {
        switch state {
        case .loading:
            HorizontalRow(isScrollEnabled: false) {
                ForEach(0..<3, id: \.self) { _ in PodcastCardPlaceholder() }
            }
            .shimmer()
            .sectionLoading(section)
        case .loaded(let cards):
            HorizontalRow {
                ForEach(Array(cards.enumerated()), id: \.offset) { index, card in
                    let sourceID = "\(section.rawValue).\(index)"
                    NavigationLink(value: PodcastRoute(card, transitionSourceID: sourceID)) {
                        PodcastCardView(card: card, zoomSourceID: sourceID, zoomNamespace: zoomNamespace)
                    }
                    .buttonStyle(PressableStyle())
                    .accessibilityLabel(Text(card.title))
                    .accessibilityIdentifier("home.section.\(section.rawValue).item.\(index)")
                }
            }
        case .empty:
            SectionMessage(section: section, isFailure: false, retry: retry)
        case .failed:
            SectionMessage(section: section, isFailure: true, retry: retry)
        }
    }
}

/// "Live Podcasts": a horizontal pager, 3 rows per page, each page 90 % of the width so the next
/// one peeks in (Android `LiveRow`, `PageSize.Fixed(screenWidth * 0.9)`).
private struct LivePager: View {
    let state: SectionState<[LiveRowItem]>
    let retry: () -> Void

    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        switch state {
        case .loading:
            VStack(alignment: .leading, spacing: 16) {
                ForEach(0..<LivePaging.pageSize, id: \.self) { _ in ListRowPlaceholder() }
            }
            .padding(.horizontal, 16)
            .shimmer()
            .sectionLoading(.live)
        case .loaded(let pages):
            HorizontalRow {
                ForEach(Array(pages.enumerated()), id: \.offset) { pageIndex, page in
                    VStack(alignment: .leading, spacing: 16) {
                        ForEach(Array(page.enumerated()), id: \.offset) { row, item in
                            Button {
                                let all = pages.flatMap { $0 }
                                environment.play(PlaybackRequest(live: all, startIndex: pageIndex * LivePaging.pageSize + row))
                            } label: {
                                ListRow(imageURL: item.imageURL, title: item.title, subtitle: item.subtitle)
                            }
                            .buttonStyle(PressableStyle())
                            .accessibilityLabel(Text(item.title))
                            .accessibilityValue(Text(item.subtitle))
                            .accessibilityIdentifier("home.section.live.item.\(pageIndex * LivePaging.pageSize + row)")
                        }
                    }
                    .frame(maxHeight: .infinity, alignment: .top)
                    .containerRelativeFrame(.horizontal) { width, _ in width * 0.9 }
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("home.live.page.\(pageIndex)")
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("home.live.pager")
            #if DEBUG
            .accessibilityValue(Text(verbatim: "count=\(pages.reduce(0) { $0 + $1.count })"))
            #endif
        case .empty:
            SectionMessage(section: .live, isFailure: false, retry: retry)
        case .failed:
            SectionMessage(section: .live, isFailure: true, retry: retry)
        }
    }
}

/// "Có thể bạn yêu thích": a vertical list (Android `FavouriteWidget`), lazy because it holds up
/// to 100 rows.
private struct ForYouList: View {
    let state: SectionState<PodcastCard>
    let zoomNamespace: Namespace.ID
    let retry: () -> Void

    var body: some View {
        switch state {
        case .loading:
            VStack(alignment: .leading, spacing: 16) {
                ForEach(0..<5, id: \.self) { _ in ListRowPlaceholder() }
            }
            .padding(.horizontal, 16)
            .shimmer()
            .sectionLoading(.forYou)
        case .loaded(let cards):
            LazyVStack(alignment: .leading, spacing: 16) {
                ForEach(Array(cards.enumerated()), id: \.offset) { index, card in
                    let sourceID = "\(HomeSection.forYou.rawValue).\(index)"
                    NavigationLink(value: PodcastRoute(card, transitionSourceID: sourceID)) {
                        ListRow(
                            imageURL: card.imageURL,
                            title: card.title,
                            subtitle: card.summary,
                            zoomSourceID: sourceID,
                            zoomNamespace: zoomNamespace
                        )
                    }
                    .buttonStyle(PressableStyle())
                    .accessibilityLabel(Text(card.title))
                    .accessibilityIdentifier("home.section.forYou.item.\(index)")
                }
            }
            .padding(.horizontal, 16)
        case .empty:
            SectionMessage(section: .forYou, isFailure: false, retry: retry)
        case .failed:
            SectionMessage(section: .forYou, isFailure: true, retry: retry)
        }
    }
}

/// Podcast Index attribution, last item of Home.
private struct AttributionFooter: View {
    var body: some View {
        Text("home.attribution")
            .font(.footnote)
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 16)
            .padding(.top, 24)
            .padding(.bottom, 32)
            .accessibilityIdentifier("home.attribution")
    }
}

/// Empty or failed section: a message and its own "Tải lại".
private struct SectionMessage: View {
    let section: HomeSection
    let isFailure: Bool
    let retry: () -> Void

    var body: some View {
        let message: LocalizedStringKey = isFailure ? "section.error" : "section.empty"
        VStack(spacing: 12) {
            Image(systemName: isFailure ? "wifi.exclamationmark" : "tray")
                .font(.title2)
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .accessibilityIdentifier("home.section.\(section.rawValue).\(isFailure ? "error" : "empty")")
            Button("action.retry", action: retry)
                .glassButton()
                .tint(Color.brandPrimary)
                .accessibilityIdentifier("home.section.\(section.rawValue).retry")
        }
        .frame(maxWidth: .infinity, minHeight: 160)
        .padding(16)
        .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .padding(.horizontal, 16)
    }
}

// MARK: - Building blocks

/// Horizontally scrolling row with 16 pt side margins and 12 pt spacing, snapping to items.
private struct HorizontalRow<Content: View>: View {
    var isScrollEnabled = true
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(alignment: .top, spacing: 12) {
                content
            }
            .scrollTargetLayout()
        }
        .contentMargins(.horizontal, 16, for: .scrollContent)
        .scrollTargetBehavior(.viewAligned)
        .scrollDisabled(!isScrollEnabled)
    }
}

/// Android `TrendingWidget`: square cover, one-line title, three-line description.
private struct PodcastCardView: View {
    static let width: CGFloat = 250
    let card: PodcastCard
    let zoomSourceID: String
    let zoomNamespace: Namespace.ID

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ArtworkImage(url: card.imageURL, cornerRadius: 10)
                .frame(width: Self.width, height: Self.width)
                .zoomTransitionSource(id: zoomSourceID, in: zoomNamespace)
            Text(card.title)
                .font(.headline)
                .foregroundStyle(.primary)
                .lineLimit(1)
                .padding(.top, 12)
            Text(card.summary)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(3, reservesSpace: true)
                .padding(.top, 2)
        }
        .frame(width: Self.width, alignment: .leading)
        .multilineTextAlignment(.leading)
        .contentShape(Rectangle())
    }
}

/// Android `AsyncTrendingWidget`.
private struct PodcastCardPlaceholder: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(.quaternary)
                .frame(width: PodcastCardView.width, height: PodcastCardView.width)
            Text(verbatim: "Podcast title placeholder")
                .font(.headline)
                .padding(.top, 12)
            Text(verbatim: "Podcast description placeholder that spans two lines of text")
                .font(.subheadline)
                .lineLimit(3, reservesSpace: true)
                .padding(.top, 2)
        }
        .frame(width: PodcastCardView.width, alignment: .leading)
    }
}

/// 60 pt cover, one-line title, up to three lines below (Android `LiveEpisodeWidgets` and
/// `FavouriteWidget`).
private struct ListRow: View {
    let imageURL: String?
    let title: String
    let subtitle: String
    /// Zoom source of a row that opens Detail (forYou); Live rows have none.
    var zoomSourceID: String?
    var zoomNamespace: Namespace.ID?

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ArtworkImage(url: imageURL, cornerRadius: 12)
                .frame(width: 60, height: 60)
                .optionalZoomSource(id: zoomSourceID, in: zoomNamespace)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .multilineTextAlignment(.leading)
        }
        .contentShape(Rectangle())
    }
}

private struct ListRowPlaceholder: View {
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(.quaternary)
                .frame(width: 60, height: 60)
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: "Podcast title placeholder")
                    .font(.headline)
                Text(verbatim: "Podcast subtitle")
                    .font(.subheadline)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Slight scale while pressed, like the cards on Android.
private struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(duration: 0.25), value: configuration.isPressed)
    }
}

private extension View {
    @ViewBuilder
    func optionalZoomSource(id: String?, in namespace: Namespace.ID?) -> some View {
        if let namespace {
            zoomTransitionSource(id: id, in: namespace)
        } else {
            self
        }
    }

    /// One accessibility element `home.section.<name>.loading` over a shimmering placeholder
    /// (the shimmer hides its own content from VoiceOver).
    func sectionLoading(_ section: HomeSection) -> some View {
        accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("home.loading"))
            .accessibilityIdentifier("home.section.\(section.rawValue).loading")
    }
}
