import SwiftUI
@preconcurrency import Shared

/// "Podcast theo vị trí" on Home (Android `LocationSection`): one chip per podcast location
/// (priority order) and, for the chosen one, a featured card (#1) above a three-row ranked chart, so
/// it reads differently from the plain card rows around it. "Sắp xếp" opens the location editor.
/// The chips go back to the primary location whenever the list is edited or reordered.
struct HomeLocationSection: View {
    let model: HomeModel
    let zoomNamespace: Namespace.ID
    let manage: () -> Void

    @Environment(AppEnvironment.self) private var environment
    @State private var selectedRegion: String?

    /// Shows of the ranked grid after the top pick (Android `items.drop(1).take(15)`).
    static let gridLimit = 15

    var body: some View {
        let locations = model.locations
        if let selected = locations.first(where: { $0.region == selectedRegion }) ?? locations.first {
            VStack(alignment: .leading, spacing: 0) {
                header
                LocationChips(locations: locations, selectedRegion: selected.region, identifierPrefix: "home.location.chip") { region in
                    trackChip(region)
                    withAnimation(.easeInOut(duration: 0.2)) { selectedRegion = region }
                }
                content(for: selected)
                    .id(selected.region)
                    .transition(.opacity)
            }
            .onChange(of: locations.map(\.region)) { selectedRegion = nil }
        }
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text("location.section.title")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(Color.textTitle)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("home.section.location")
                Text("location.section.subtitle")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            Button(action: manage) {
                Label("location.manage", systemImage: "slider.horizontal.3")
                    .font(.subheadline.weight(.semibold))
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .tint(Color.brandPrimary)
            .accessibilityIdentifier("home.location.manage")
        }
        .padding(.horizontal, 16)
        .padding(.top, 20)
    }

    @ViewBuilder
    private func content(for location: PodcastLocationItem) -> some View {
        switch model.displayedLocationPodcasts(location.region) {
        case nil, .loading?:
            LocationPlaceholder()
                .shimmer()
                .sectionLoading(.location)
        case .empty?:
            Text("location.empty")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 24)
                .accessibilityIdentifier("home.section.location.empty")
        case .failed?:
            SectionMessage(section: .location, isFailure: true) {
                Task { await model.retryLocation(location.region) }
            }
        case .loaded(let cards)?:
            if let top = cards.first {
                VStack(alignment: .leading, spacing: 12) {
                    NavigationLink(value: route(top, index: 0, region: location.region)) {
                        TopPickCard(card: top, zoomSourceID: sourceID(0, location.region), zoomNamespace: zoomNamespace)
                    }
                    .buttonStyle(PressableStyle())
                    .hideShowContextMenu(feedId: top.id, title: top.title, imageURL: top.imageURL, screen: HomeView.analyticsScreen)
                    .accessibilityLabel(Text(top.title))
                    .accessibilityHint(Text("location.topPick"))
                    .accessibilityIdentifier("home.section.location.item.0")
                    .padding(.horizontal, 16)

                    RankedGrid(
                        cards: Array(cards.dropFirst().prefix(Self.gridLimit)),
                        region: location.region,
                        zoomNamespace: zoomNamespace
                    )
                }
            }
        }
    }

    private func sourceID(_ index: Int, _ region: String) -> String {
        "\(HomeSection.location.rawValue).\(region).\(index)"
    }

    private func route(_ card: PodcastCard, index: Int, region: String) -> DiscoverRoute {
        .podcast(PodcastRoute(card, transitionSourceID: sourceID(index, region)))
    }

    private func trackChip(_ region: String) {
        let events = AnalyticsEvents.shared
        environment.analytics.trackEvent(
            name: events.LOCATION_CHIP_SELECT,
            screen: HomeView.analyticsScreen,
            params: [events.PARAM_REGION: region, events.PARAM_SECTION: events.SECTION_LOCATION]
        )
    }
}

/// "★ Nổi bật nhất": the location's #1 show, a 112 pt cover on a soft brand gradient.
private struct TopPickCard: View {
    let card: PodcastCard
    let zoomSourceID: String
    let zoomNamespace: Namespace.ID

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        HStack(alignment: .center, spacing: 14) {
            ArtworkImage(url: card.imageURL, cornerRadius: 14)
                .frame(width: 112, height: 112)
                .zoomTransitionSource(id: zoomSourceID, in: zoomNamespace)
            VStack(alignment: .leading, spacing: 4) {
                Label("location.topPick", systemImage: "star.fill")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.brandPrimary)
                    .labelStyle(.titleAndIcon)
                Text(card.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                Text(card.summary)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .multilineTextAlignment(.leading)
        }
        .padding(12)
        .background(
            LinearGradient(
                colors: [Color.brandPrimary.opacity(0.22), Color.brandSecondary.opacity(0.10)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: shape
        )
        .overlay(shape.stroke(Color.brandPrimary.opacity(0.15), lineWidth: 1))
        .contentShape(shape)
    }
}

/// #2… of the location: three rows that scroll sideways, a rank number beside each cover.
private struct RankedGrid: View {
    let cards: [PodcastCard]
    let region: String
    let zoomNamespace: Namespace.ID

    static let rowHeight: CGFloat = 64

    var body: some View {
        if !cards.isEmpty {
            let rows = Array(repeating: GridItem(.fixed(Self.rowHeight), spacing: 8), count: min(3, cards.count))
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHGrid(rows: rows, spacing: 16) {
                    ForEach(Array(cards.enumerated()), id: \.element.id) { offset, card in
                        let index = offset + 1
                        let sourceID = "\(HomeSection.location.rawValue).\(region).\(index)"
                        NavigationLink(value: DiscoverRoute.podcast(PodcastRoute(card, transitionSourceID: sourceID))) {
                            RankedRow(card: card, rank: index + 1, zoomSourceID: sourceID, zoomNamespace: zoomNamespace)
                        }
                        .buttonStyle(PressableStyle())
                        .hideShowContextMenu(feedId: card.id, title: card.title, imageURL: card.imageURL, screen: HomeView.analyticsScreen)
                        .accessibilityLabel(Text(verbatim: "\(index + 1). \(card.title)"))
                        .accessibilityIdentifier("home.section.location.item.\(index)")
                    }
                }
                .scrollTargetLayout()
            }
            .contentMargins(.horizontal, 16, for: .scrollContent)
            .scrollTargetBehavior(.viewAligned)
            .padding(.bottom, 4)
        }
    }
}

private struct RankedRow: View {
    let card: PodcastCard
    let rank: Int
    let zoomSourceID: String
    let zoomNamespace: Namespace.ID

    var body: some View {
        HStack(spacing: 0) {
            ArtworkImage(url: card.imageURL, cornerRadius: 12)
                .frame(width: RankedGrid.rowHeight, height: RankedGrid.rowHeight)
                .zoomTransitionSource(id: zoomSourceID, in: zoomNamespace)
            Text(verbatim: "\(rank)")
                .font(.title3.weight(.bold).monospacedDigit())
                .foregroundStyle(Color.brandPrimary)
                .frame(width: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(card.title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                Text(card.summary)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .multilineTextAlignment(.leading)
        }
        .frame(width: 280, height: RankedGrid.rowHeight)
        .contentShape(Rectangle())
    }
}

private struct LocationPlaceholder: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(.quaternary)
                .frame(height: 136)
            ForEach(0..<3, id: \.self) { _ in
                HStack(spacing: 12) {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(.quaternary)
                        .frame(width: RankedGrid.rowHeight, height: RankedGrid.rowHeight)
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(.quaternary)
                        .frame(width: 180, height: 16)
                }
            }
        }
        .padding(.horizontal, 16)
    }
}

/// One pill per location (flag + country), the selected one tinted (Android `LocationChips`): the
/// interactive glass capsules of the Search mode chips (Material on iOS 17/18).
struct LocationChips: View {
    let locations: [PodcastLocationItem]
    let selectedRegion: String
    /// `<prefix>.<region>` is each chip's accessibility id.
    let identifierPrefix: String
    let select: (String) -> Void

    @Environment(\.locale) private var locale

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            GlassGroup(spacing: 4) {
                HStack(spacing: 8) {
                    ForEach(locations) { location in
                        let isSelected = location.region == selectedRegion
                        Button {
                            select(location.region)
                        } label: {
                            HStack(spacing: 6) {
                                Text(verbatim: location.flag)
                                Text(verbatim: location.name(in: locale))
                                    .font(isSelected ? .subheadline.weight(.semibold) : .subheadline)
                            }
                            .foregroundStyle(isSelected ? AnyShapeStyle(.background) : AnyShapeStyle(.primary))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .glassSurface(in: Capsule(), tint: isSelected ? .brandPrimary : nil, interactive: true)
                            .padding(.vertical, 4)
                            .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .animation(.easeInOut(duration: 0.2), value: isSelected)
                        .accessibilityAddTraits(isSelected ? .isSelected : [])
                        .accessibilityIdentifier("\(identifierPrefix).\(location.region)")
                    }
                }
            }
        }
        .contentMargins(.horizontal, 16, for: .scrollContent)
        .padding(.vertical, 6)
    }
}
