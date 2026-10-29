import SwiftUI

/// Pushes `CategoryPodcastsView` for a category chip, with a model built by `AppEnvironment`.
struct CategoryPodcastsDestination: View {
    let route: CategoryPodcastsRoute

    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        CategoryPodcastsView(model: environment.makeCategoryPodcastsModel(for: route.category))
    }
}

/// Podcasts trending in one category (features-spec §3.9 [Sửa]), titled with the category name.
/// A tap opens Podcast detail on the Search stack (the `PodcastRoute` destination of
/// `SearchView`).
struct CategoryPodcastsView: View {
    @State private var model: CategoryPodcastsModel

    init(model: CategoryPodcastsModel) {
        _model = State(initialValue: model)
    }

    var body: some View {
        ScrollView {
            // The id sits on the content, not on the scroll view (see `HomeView`).
            VStack(spacing: 0) {
                content
            }
            .padding(.bottom, 24)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("categoryPodcasts.root")
        }
        .navigationTitle(Text(verbatim: model.category.name))
        .toolbarTitleDisplayMode(.inline)
        .task { await model.loadIfNeeded() }
    }

    @ViewBuilder
    private var content: some View {
        switch model.state {
        case .loading:
            VStack(spacing: 0) {
                ForEach(0..<8, id: \.self) { _ in SearchResultRowPlaceholder() }
            }
            .shimmer()
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("search.loading"))
            .accessibilityIdentifier("categoryPodcasts.loading")
        case .loaded(let cards):
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(Array(cards.enumerated()), id: \.offset) { index, card in
                    NavigationLink(value: PodcastRoute(card)) {
                        SearchResultRow(
                            imageURL: card.imageURL,
                            title: card.title,
                            summary: card.summary,
                            identifier: "categoryPodcasts.item.\(index)"
                        )
                    }
                    .buttonStyle(RowButtonStyle())
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(Text(verbatim: card.title))
                    .accessibilityIdentifier("categoryPodcasts.item.\(index)")
                }
            }
        case .empty:
            LoadErrorMessage(
                icon: "tray",
                text: "section.empty",
                textIdentifier: "categoryPodcasts.empty",
                retryIdentifier: "categoryPodcasts.retry"
            ) {
                Task { await model.retry() }
            }
            .padding(.top, 24)
        case .failed:
            LoadErrorMessage(textIdentifier: "categoryPodcasts.error", retryIdentifier: "categoryPodcasts.retry") {
                Task { await model.retry() }
            }
            .padding(.top, 24)
        }
    }
}
