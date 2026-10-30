import SwiftUI

/// Pushes `PodcastDetailView` for a route, with a model built by `AppEnvironment` (the data
/// source and the player seam live there). Every stack's `navigationDestination(for:
/// PodcastRoute.self)` uses it.
struct PodcastDetailDestination: View {
    let route: PodcastRoute

    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        PodcastDetailView(model: environment.makePodcastDetailModel(for: route))
    }
}

/// Podcast detail (Android `PodcastDetailScreen` + `Header`): blurred artwork behind a 150 pt
/// cover, the title and the full description with tappable links, then the episodes. A tap on
/// an episode goes to the `PlaybackController` seam and stays here (Player is M3).
///
/// The system back button and edge swipe replace Android's floating back button; no ads, no
/// Favourite/Share button ([iOS+], out of scope).
struct PodcastDetailView: View {
    @State private var model: PodcastDetailModel
    @State private var isHideConfirmationPresented = false
    /// Address shown when no app could open the report mail.
    @State private var reportFallbackEmail: String?

    @Environment(AppEnvironment.self) private var environment
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    init(model: PodcastDetailModel) {
        _model = State(initialValue: model)
    }

    /// Screen name of Podcast detail in analytics.
    static let analyticsScreen = "PodcastDetail"

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                DetailHeader(podcast: model.podcast)
                if model.isShowingSavedEpisodes {
                    OfflineBanner()
                }
                if model.searchState == .idle {
                    EpisodesSection(model: model)
                        .padding(.bottom, 24)
                } else {
                    SearchResultsSection(model: model)
                        .padding(.bottom, 24)
                }
            }
        }
        .modifier(ShowSearchField(model: model))
        .accessibilityIdentifier("detail.root")
        .navigationTitle(Text(verbatim: model.podcast.title))
        .toolbarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                moreMenu
            }
        }
        .confirmationDialog(
            Text("detail.hide.confirmTitle"),
            isPresented: $isHideConfirmationPresented,
            titleVisibility: .visible
        ) {
            Button(role: .destructive) {
                hideShow()
            } label: {
                Text("detail.hide.confirm")
            }
            .accessibilityIdentifier("hide.confirm")
            Button(role: .cancel) {} label: {
                Text("action.cancel")
            }
        } message: {
            Text("detail.hide.confirmMessage")
        }
        .contentReportFallbackAlert(email: $reportFallbackEmail)
        .task { await model.loadIfNeeded() }
        .alert(Text("detail.error.title"), isPresented: $model.isShowingErrorAlert) {
            Button("action.retry") {
                Task { await model.retry() }
            }
            Button("action.cancel", role: .cancel) {
                model.dismissError()
            }
        } message: {
            Text("detail.error.message")
        }
    }

    /// `…` of the navigation bar: "Ẩn show này" and, when an address is configured, "Báo cáo
    /// nội dung" (M6 §3.5).
    private var moreMenu: some View {
        Menu {
            Button {
                isHideConfirmationPresented = true
            } label: {
                Label("show.hide", systemImage: "eye.slash")
            }
            .accessibilityIdentifier("detail.hide")
            if environment.contentReporter.isAvailable {
                Button {
                    report()
                } label: {
                    Label("report.action", systemImage: "exclamationmark.bubble")
                }
                .accessibilityIdentifier("detail.report")
            }
        } label: {
            Image(systemName: "ellipsis")
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .accessibilityLabel(Text("detail.menu"))
        .accessibilityIdentifier("detail.menu")
    }

    /// Hides the show (saved, logged, dropped from Home/Search at once) and leaves Detail.
    private func hideShow() {
        let podcast = model.podcast
        let hiddenShows = environment.hiddenShows
        Task {
            await hiddenShows.hide(
                feedId: podcast.feedId,
                title: podcast.title,
                imageURL: podcast.imageURL,
                screen: Self.analyticsScreen
            )
        }
        dismiss()
    }

    private func report() {
        let openURL = openURL
        environment.contentReporter.report(
            kind: .feed,
            feedId: model.podcast.feedId,
            episodeId: nil,
            screen: Self.analyticsScreen,
            open: { url, completion in openURL(url, completion: completion) },
            failed: { email in reportFallbackEmail = email }
        )
    }
}

// MARK: - Search in show (M6 §3.3)

/// `.searchable` ("Tìm trong show") only on a show with more than 20 episodes.
private struct ShowSearchField: ViewModifier {
    let model: PodcastDetailModel

    func body(content: Content) -> some View {
        if model.isSearchAvailable {
            content.searchable(
                text: Binding(get: { model.searchQuery }, set: { model.searchQueryChanged($0) }),
                placement: .navigationBarDrawer(displayMode: .automatic),
                prompt: Text("detail.search.prompt")
            )
        } else {
            content
        }
    }
}

/// Results of "Tìm trong show": rows like the list (a tap plays the results), "Không tìm thấy tập
/// phù hợp", and the footnotes when the search does not cover every episode.
private struct SearchResultsSection: View {
    let model: PodcastDetailModel

    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            switch model.searchState {
            case .idle:
                EmptyView()
            case .loading:
                ProgressView()
                    .frame(maxWidth: .infinity, minHeight: 120)
                    .accessibilityLabel(Text("detail.search.loading"))
                    .accessibilityIdentifier("detail.search.loading")
            case .results(let rows):
                let nowPlayingID = model.nowPlayingEpisodeID
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                        Button {
                            if model.playSearchResult(index: index) {
                                environment.isPlayerPresented = true
                            }
                        } label: {
                            EpisodeRowView(row: row, index: index, isNowPlaying: row.id == nowPlayingID)
                        }
                        .buttonStyle(EpisodeButtonStyle())
                        .accessibilityElement(children: .contain)
                        .accessibilityAddTraits(.isButton)
                        .accessibilityIdentifier("detail.search.result.\(index)")
                        if index < rows.count - 1 {
                            GradientDivider()
                        }
                    }
                }
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("detail.search.results")
            case .noMatch:
                DetailMessage(icon: "magnifyingglass", text: "detail.search.empty", identifier: "detail.search.empty")
            }
            footnotes
        }
    }

    @ViewBuilder
    private var footnotes: some View {
        if model.searchState != .loading {
            if model.isSearchingOnScreenOnly {
                SearchFootnote(text: Text("detail.search.onScreenOnly"), identifier: "detail.search.onScreenOnly")
            } else if let count = model.searchFootnoteCount {
                SearchFootnote(text: Text("detail.search.footnote \(count)"), identifier: "detail.search.footnote")
            }
        }
    }
}

private struct SearchFootnote: View {
    let text: Text
    let identifier: String

    var body: some View {
        text
            .font(.footnote)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .accessibilityIdentifier(identifier)
    }
}

// MARK: - Header

private struct DetailHeader: View {
    let podcast: PodcastRoute

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 16) {
                ArtworkImage(url: podcast.imageURL, cornerRadius: 20, accessibilityLabel: podcast.title)
                    .frame(width: 150, height: 150)
                    .shadow(color: .black.opacity(0.25), radius: 12, y: 6)
                    .accessibilityIdentifier("detail.cover")
                Text(verbatim: podcast.title)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("detail.title")
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 16)
            .padding(.top, 16)
            .padding(.bottom, 20)
            .background(alignment: .bottom) {
                HeaderBackdrop(imageURL: podcast.imageURL)
            }

            if !podcast.summary.isEmpty {
                Text(DescriptionText.attributed(podcast.summary))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .tint(Color.brandPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
                    .accessibilityIdentifier("detail.description")
            }
        }
    }
}

/// Blurred cover behind the header (Android: the cover blurred at alpha 0.7 over a gradient),
/// fading into the page background so the title stays readable. It runs up under the
/// navigation bar.
private struct HeaderBackdrop: View {
    let imageURL: String?

    var body: some View {
        GeometryReader { proxy in
            ArtworkImage(url: imageURL, cornerRadius: 0)
                .frame(width: proxy.size.width, height: proxy.size.height)
                .blur(radius: 30, opaque: true)
                .opacity(0.7)
                .overlay {
                    LinearGradient(
                        colors: [Color(.systemBackground).opacity(0.1), Color(.systemBackground)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                }
                .clipped()
        }
        .padding(.top, -400)
        .accessibilityHidden(true)
    }
}

// MARK: - Episodes

private struct EpisodesSection: View {
    let model: PodcastDetailModel

    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        switch model.state {
        case .loading:
            VStack(spacing: 0) {
                ForEach(0..<8, id: \.self) { _ in EpisodeRowPlaceholder() }
            }
            .shimmer()
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("detail.loading"))
            .accessibilityIdentifier("detail.episodes.loading")
        case .loaded(let rows):
            let nowPlayingID = model.nowPlayingEpisodeID
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                    Button {
                        if model.play(index: index) {
                            environment.isPlayerPresented = true
                        }
                    } label: {
                        EpisodeRowView(row: row, index: index, isNowPlaying: row.id == nowPlayingID)
                    }
                    .buttonStyle(EpisodeButtonStyle())
                    .accessibilityElement(children: .contain)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityIdentifier("detail.episode.\(index)")
                    if index < rows.count - 1 {
                        GradientDivider()
                    }
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("detail.episodes")
            #if DEBUG
            .accessibilityValue(Text(verbatim: "count=\(rows.count)"))
            #endif
        case .empty:
            DetailMessage(icon: "tray", text: "detail.episodes.empty", identifier: "detail.episodes.empty")
        case .explicitHidden:
            DetailMessage(icon: "eye.slash", text: "detail.explicitHidden", identifier: "detail.explicitHidden")
        case .failed:
            DetailMessage(icon: "wifi.exclamationmark", text: "detail.episodes.error", identifier: "detail.episodes.error") {
                Task { await model.retry() }
            }
        }
    }
}

/// E10 failed and the list is the one saved on the device (M4 §3.3 [Sửa]).
private struct OfflineBanner: View {
    var body: some View {
        Label {
            Text("detail.offlineBanner")
        } icon: {
            Image(systemName: "wifi.slash")
        }
        .font(.footnote)
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("detail.offlineBanner")
    }
}

/// Empty, explicit-hidden or failed list, with "Tải lại" when `retry` is given.
private struct DetailMessage: View {
    let icon: String
    let text: LocalizedStringKey
    let identifier: String
    var retry: (() -> Void)?

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .accessibilityIdentifier(identifier)
            if let retry {
                Button("action.retry", action: retry)
                    .glassButton()
                    .tint(Color.brandPrimary)
                    .accessibilityIdentifier("detail.episodes.retry")
            }
        }
        .frame(maxWidth: .infinity, minHeight: 160)
        .padding(16)
        .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }
}

/// Android separates episodes with a gradient line.
private struct GradientDivider: View {
    var body: some View {
        LinearGradient(
            colors: [Color.brandPrimary.opacity(0.05), Color.brandSecondary.opacity(0.35), Color.brandPrimary.opacity(0.05)],
            startPoint: .leading,
            endPoint: .trailing
        )
        .frame(height: 1)
        .padding(.horizontal, 16)
        .accessibilityHidden(true)
    }
}

/// Row highlight while pressed.
private struct EpisodeButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed ? Color.primary.opacity(0.06) : .clear)
    }
}
