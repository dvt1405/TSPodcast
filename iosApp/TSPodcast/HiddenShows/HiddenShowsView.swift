import SwiftUI

/// Settings → "Show đã ẩn" (`hiddenShows.root`): every hidden show with "Bỏ ẩn", or the empty text.
struct HiddenShowsView: View {
    let model: HiddenShowsModel

    var body: some View {
        List {
            if model.items.isEmpty {
                Text("hiddenShows.empty")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 24)
                    .accessibilityIdentifier("hiddenShows.empty")
            } else {
                Section {
                    ForEach(Array(model.items.enumerated()), id: \.element.id) { index, show in
                        HStack(spacing: 12) {
                            ArtworkImage(url: show.imageURL, cornerRadius: 8)
                                .frame(width: 44, height: 44)
                                .accessibilityHidden(true)
                            Text(verbatim: show.title)
                                .lineLimit(2)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Button {
                                Task { await model.unhide(feedId: show.feedId) }
                            } label: {
                                Text("hiddenShows.unhide")
                            }
                            .buttonStyle(.bordered)
                            .frame(minHeight: 44)
                            .accessibilityIdentifier("hiddenShows.unhide.\(index)")
                        }
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier("hiddenShows.item.\(index)")
                    }
                } footer: {
                    Text("hiddenShows.footer")
                }
            }
        }
        .navigationTitle(Text("hiddenShows.title"))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityIdentifier("hiddenShows.root")
        .task { await model.load() }
    }
}
