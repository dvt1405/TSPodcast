import SwiftUI

/// Root of the "Yêu thích" tab (Android `FavouriteScreen`): the favourites newest first, or
/// Android's empty state (`EmptyFavouriteWidget`: illustration, "Không có chương trình yêu
/// thích." and "Khám phá thêm", which switches to the Discover tab). No ad banner (D2).
struct FavouriteRootView: View {
    @Bindable var model: FavouriteModel
    let onDiscoverMore: () -> Void

    var body: some View {
        content
            .navigationDestination(for: PodcastRoute.self) { route in
                PodcastDetailDestination(route: route)
            }
            .alert(
                Text("favourite.unavailable.title"),
                isPresented: Binding(
                    get: { model.unavailable != nil },
                    set: { if !$0 { model.dismissUnavailable() } }
                )
            ) {
                Button("favourite.unavailable.remove", role: .destructive) {
                    Task { await model.removeUnavailable() }
                }
                .accessibilityIdentifier("favourite.unavailable.remove")
                Button("action.close", role: .cancel) {
                    model.dismissUnavailable()
                }
                .accessibilityIdentifier("favourite.unavailable.close")
            } message: {
                Text("favourite.unavailable.message")
                    .accessibilityIdentifier("favourite.unavailable")
            }
    }

    @ViewBuilder
    private var content: some View {
        switch model.state {
        case .loading:
            Color.clear
                .accessibilityIdentifier("favourite.root")
        case .empty:
            EmptyFavourites(onDiscoverMore: onDiscoverMore)
        case .loaded(let rows):
            FavouriteList(rows: rows) { index in
                Task { await model.select(index: index) }
            }
        }
    }
}

private struct FavouriteList: View {
    let rows: [FavouriteRow]
    let select: (Int) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                    Button {
                        select(index)
                    } label: {
                        FavouriteRowView(row: row)
                    }
                    .buttonStyle(RowButtonStyle())
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(verbatim: row.accessibilityLabel))
                    .accessibilityAddTraits(.isButton)
                    .accessibilityIdentifier("favourite.item.\(index)")
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("favourite.root")
        }
        .accessibilityIdentifier("favourite.list")
        #if DEBUG
        .accessibilityValue(Text(verbatim: "count=\(rows.count)"))
        #endif
    }
}

/// Android `FavouriteItemWidget`: 60 pt image with 12 pt corners, the title and the plain-text
/// description.
private struct FavouriteRowView: View {
    let row: FavouriteRow

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ArtworkImage(url: row.imageURL, cornerRadius: 12)
                .frame(width: 60, height: 60)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: row.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                if !row.summary.isEmpty {
                    Text(verbatim: row.summary)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
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

/// Android `EmptyFavouriteWidget`.
private struct EmptyFavourites: View {
    let onDiscoverMore: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Image("empty_favourite")
                    .accessibilityHidden(true)
                Text("favourite.empty")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("favourite.empty")
                Button("favourite.discoverMore", action: onDiscoverMore)
                    .buttonStyle(GradientButtonStyle())
                    .accessibilityIdentifier("favourite.discoverMore")
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity)
            .containerRelativeFrame(.vertical, alignment: .center) { height, _ in height * 0.8 }
        }
        .accessibilityIdentifier("favourite.root")
    }
}
