import SwiftUI

/// The "Tìm kiếm" tab (Android `SearchScreen`, features-spec §3.9): the search field in the
/// navigation bar, the "Tất cả"/"Nhạc" chips always under it, then
/// - idle: "Thử tìm kiếm: Podcast hay" and the E9 category chips ([Sửa]: Android loads them but
///   never shows them), each opening the podcasts trending in that category;
/// - loading, results ("Kết quả hàng đầu cho: q" / "Nhạc cho: q"), empty (+ the music hint) or
///   failed with "Tải lại" ([Sửa]: Android showed a failure as "no results").
///
/// The chips are drawn here rather than with `.searchScopes`, which only shows while the field
/// is active; Android always shows them. Results and categories push on this tab's stack.
struct SearchView: View {
    let model: SearchModel

    @Environment(AppEnvironment.self) private var environment
    @State private var categories: [CategoryItem] = []

    var body: some View {
        VStack(spacing: 0) {
            SearchModeChips(selected: model.mode) { mode in
                model.select(mode: mode)
            }
            ScrollView {
                content
                    .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.immediately)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("search.root")
        .searchable(
            text: Binding(get: { model.query }, set: { model.queryChanged($0) }),
            placement: .navigationBarDrawer(displayMode: .always),
            prompt: Text("search.prompt")
        )
        .onSubmit(of: .search) { model.submit() }
        .task {
            guard categories.isEmpty else { return }
            categories = await environment.categoryCatalog.categories()
        }
        .navigationDestination(for: PodcastRoute.self) { route in
            PodcastDetailDestination(route: route)
        }
        .navigationDestination(for: CategoryPodcastsRoute.self) { route in
            CategoryPodcastsDestination(route: route)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .idle:
            SearchIdleContent(categories: categories)
        case .loading:
            ProgressView()
                .controlSize(.large)
                .frame(maxWidth: .infinity)
                .padding(.top, 48)
                .accessibilityLabel(Text("search.loading"))
                .accessibilityIdentifier("search.loading")
        case .results(let items):
            SearchResultsList(items: items, query: model.shownQuery, mode: model.shownMode)
        case .empty:
            SearchEmptyContent(query: model.shownQuery, mode: model.shownMode)
        case .failed:
            SearchErrorContent {
                model.retry()
            }
        }
    }
}

// MARK: - Mode chips

/// Android `SearchModeFilter`: two pill chips, the selected one tinted.
private struct SearchModeChips: View {
    let selected: SearchScope
    let select: (SearchScope) -> Void

    var body: some View {
        HStack(spacing: 8) {
            ForEach(SearchScope.allCases, id: \.self) { mode in
                let isSelected = mode == selected
                Button {
                    select(mode)
                } label: {
                    Text(mode.title)
                        .font(isSelected ? .subheadline.weight(.semibold) : .subheadline)
                        .foregroundStyle(isSelected ? Color.brandPrimary : Color.secondary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(isSelected ? Color.brandPrimary.opacity(0.1) : Color.clear, in: Capsule())
                        .overlay {
                            Capsule()
                                .strokeBorder(isSelected ? Color.brandPrimary.opacity(0.5) : Color.stroke, lineWidth: 1)
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .animation(.easeInOut(duration: 0.2), value: isSelected)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
                .accessibilityIdentifier("search.mode.\(mode.rawValue)")
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 4)
    }
}

extension SearchScope {
    var title: LocalizedStringKey {
        switch self {
        case .all: "search.mode.all"
        case .music: "search.mode.music"
        }
    }
}

// MARK: - Idle

/// Suggestion (not tappable, like Android) and the category chips.
private struct SearchIdleContent: View {
    let categories: [CategoryItem]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("search.suggestion \(String(localized: "search.suggestion.keyword"))")
                .font(.title3.weight(.semibold))
                .foregroundStyle(Color.textTitle)
                .accessibilityIdentifier("search.suggestion")
                .padding(.top, 20)

            Text("search.categories")
                .font(.headline)
                .foregroundStyle(.primary)
                .accessibilityAddTraits(.isHeader)
                .padding(.top, 24)
                .padding(.bottom, 12)

            if categories.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.top, 12)
            } else {
                FlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                    ForEach(categories) { category in
                        NavigationLink(value: CategoryPodcastsRoute(category: category)) {
                            Text(verbatim: category.name)
                                .font(.subheadline)
                                .foregroundStyle(Color.brandPrimary)
                                .lineLimit(1)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(Color.brandPrimary.opacity(0.08), in: Capsule())
                                .overlay { Capsule().strokeBorder(Color.stroke, lineWidth: 1) }
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text(verbatim: category.name))
                        .accessibilityIdentifier("search.category.\(category.id)")
                    }
                }
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("search.categories")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
    }
}

// MARK: - Results

private struct SearchResultsList: View {
    let items: [SearchResultItem]
    let query: String
    let mode: SearchScope

    var body: some View {
        LazyVStack(alignment: .leading, spacing: 0) {
            header
                .font(.title3.weight(.semibold))
                .foregroundStyle(Color.textTitle)
                .padding(.horizontal, 16)
                .padding(.top, 16)
                .padding(.bottom, 4)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("search.results.header")
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                NavigationLink(value: item.route) {
                    SearchResultRow(
                        imageURL: item.imageURL,
                        title: item.title,
                        subtitle: item.subtitle,
                        summary: item.summary,
                        identifier: "search.result.\(index)"
                    )
                }
                .buttonStyle(RowButtonStyle())
                .accessibilityElement(children: .contain)
                .accessibilityAddTraits(.isButton)
                .accessibilityIdentifier("search.result.\(index)")
                // The title stays its own `StaticText` (`search.result.<i>.title`); SwiftUI also
                // copies that id onto the link's merged button, so tests query `staticTexts`.
            }
        }
    }

    private var header: Text {
        switch mode {
        case .all: Text("search.results.all \(query)")
        case .music: Text("search.results.music \(query)")
        }
    }
}

// MARK: - Empty and error

/// Android `EmptySearchWidget`: illustration and "Không có kết quả cho: **q**", plus the music
/// hint in "Nhạc" mode.
private struct SearchEmptyContent: View {
    let query: String
    let mode: SearchScope

    var body: some View {
        VStack(spacing: 12) {
            Image("ic_empty_search")
                .resizable()
                .scaledToFit()
                .frame(width: 200, height: 200)
                .accessibilityHidden(true)
            Text("search.empty \(Text(verbatim: query).bold())")
                .font(.body)
                .multilineTextAlignment(.center)
                .accessibilityIdentifier("search.empty")
            if mode == .music {
                Text("search.empty.musicHint")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("search.empty.musicHint")
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 40)
        .padding(.top, 40)
    }
}

/// A failed search: the M1 error text and "Tải lại".
private struct SearchErrorContent: View {
    let retry: () -> Void

    var body: some View {
        LoadErrorMessage(textIdentifier: "search.error", retryIdentifier: "search.retry", retry: retry)
            .padding(.top, 24)
    }
}

/// "Không tải được nội dung" (or another message) with its "Tải lại", in a rounded box (Home's
/// section message). Shared by Search and the category list.
struct LoadErrorMessage: View {
    var icon = "wifi.exclamationmark"
    var text: LocalizedStringKey = "section.error"
    let textIdentifier: String
    let retryIdentifier: String
    let retry: () -> Void

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
                .accessibilityIdentifier(textIdentifier)
            Button("action.retry", action: retry)
                .buttonStyle(.bordered)
                .tint(Color.brandPrimary)
                .accessibilityIdentifier(retryIdentifier)
        }
        .frame(maxWidth: .infinity, minHeight: 160)
        .padding(16)
        .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .padding(.horizontal, 16)
    }
}
