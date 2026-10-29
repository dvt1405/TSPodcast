import SwiftUI

/// A podcast row of Search and of the category list (Android `SearchPodcastItem`): 70 pt cover
/// with 8 pt corners, the title, an optional "author · categories" line and the plain-text
/// description on at most 3 lines.
///
/// `identifier` is the row's accessibility id; the title gets `<identifier>.title`.
struct SearchResultRow: View {
    let imageURL: String?
    let title: String
    var subtitle: String = ""
    let summary: String
    let identifier: String

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            ArtworkImage(url: imageURL, cornerRadius: 8)
                .frame(width: 70, height: 70)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .accessibilityIdentifier("\(identifier).title")
                if !subtitle.isEmpty {
                    Text(verbatim: subtitle)
                        .font(.footnote)
                        .foregroundStyle(Color.brandSecondary)
                        .lineLimit(2)
                }
                if !summary.isEmpty {
                    Text(verbatim: summary)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                        .padding(.top, 2)
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

/// Shimmer placeholder with the shape of `SearchResultRow`.
struct SearchResultRowPlaceholder: View {
    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(.quaternary)
                .frame(width: 70, height: 70)
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: "Podcast title placeholder")
                    .font(.headline)
                Text(verbatim: "Author · Category")
                    .font(.footnote)
                Text(verbatim: "Podcast description placeholder on two lines")
                    .font(.subheadline)
                    .lineLimit(2, reservesSpace: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

/// Row highlight while pressed (the rows push Detail).
struct RowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed ? Color.primary.opacity(0.06) : .clear)
    }
}
