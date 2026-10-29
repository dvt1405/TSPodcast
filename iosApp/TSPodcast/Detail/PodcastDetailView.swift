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

    init(model: PodcastDetailModel) {
        _model = State(initialValue: model)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                DetailHeader(podcast: model.podcast)
                EpisodesSection(model: model)
                    .padding(.bottom, 24)
            }
        }
        .accessibilityIdentifier("detail.root")
        .navigationTitle(Text(verbatim: model.podcast.title))
        .toolbarTitleDisplayMode(.inline)
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
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                    Button {
                        model.play(index: index)
                    } label: {
                        EpisodeRowView(row: row, index: index)
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
                    .buttonStyle(.bordered)
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
