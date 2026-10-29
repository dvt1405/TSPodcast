import SwiftUI

/// Pushed when a podcast is tapped on Home: cover, title and plain-text description. The Podcast
/// detail milestone replaces it with the real screen (E10/E11, episodes, favourite, play).
struct PodcastDetailStubView: View {
    let podcast: PodcastCard

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ArtworkImage(url: podcast.imageURL, cornerRadius: 16)
                    .frame(width: 240, height: 240)
                    .frame(maxWidth: .infinity)
                Text(podcast.title)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(Color.textTitle)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("detail.title")
                if !podcast.summary.isEmpty {
                    Text(podcast.summary)
                        .font(.body)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(16)
        }
        .accessibilityIdentifier("detail.root")
        .toolbarTitleDisplayMode(.inline)
    }
}

#Preview {
    NavigationStack {
        PodcastDetailStubView(
            podcast: PodcastCard(id: 1, title: "Podcast", summary: "Mô tả\nhai dòng", imageURL: nil)
        )
    }
}
