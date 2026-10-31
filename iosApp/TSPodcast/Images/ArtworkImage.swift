import SwiftUI
import UIKit

extension EnvironmentValues {
    /// Loader used by `ArtworkImage`; the app injects `AppEnvironment.artworkLoader`.
    @Entry var artworkLoader: ArtworkLoader = .shared
}

/// Cover image from a PodcastIndex URL (`podcast.image`, `feedImage`…) through `ArtworkLoader`.
/// Fills the frame the caller gives it (cropped, `scaledToFill`); shows a neutral placeholder
/// while loading and when the URL is missing or the load failed.
///
/// Decorative (hidden from VoiceOver) unless `accessibilityLabel` is given.
struct ArtworkImage: View {
    let url: String?
    /// Sent as `Referer` with the request (episode artwork: `episode.link`).
    var referer: String?
    var cornerRadius: CGFloat = 12
    var accessibilityLabel: String?

    @Environment(\.artworkLoader) private var loader
    @State private var loaded: (url: String?, image: UIImage)?

    var body: some View {
        Color.clear
            .overlay {
                if let image = currentImage {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .transition(.opacity)
                } else {
                    placeholder
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: accessibilityLabel ?? ""))
            .accessibilityAddTraits(.isImage)
            .accessibilityHidden(accessibilityLabel == nil)
            .task(id: url) {
                // `.task(id:)` also reruns when a lazy row reappears: keep what is loaded.
                if let loaded, loaded.url == url { return }
                if let image = await loader.image(for: url, referer: referer) {
                    withAnimation(.easeOut(duration: 0.2)) {
                        loaded = (url, image)
                    }
                }
            }
    }

    /// The decoded image for the current URL, from state or straight from the memory cache.
    private var currentImage: UIImage? {
        if let loaded, loaded.url == url { return loaded.image }
        return loader.cachedImage(for: url)
    }

    private var placeholder: some View {
        Rectangle()
            .fill(.quaternary)
            .overlay {
                Image(systemName: "waveform")
                    .font(.title2)
                    .foregroundStyle(.secondary)
            }
    }
}

#Preview {
    HStack {
        ArtworkImage(url: nil)
            .frame(width: 120, height: 120)
        ArtworkImage(url: "http://example.invalid/a.jpg")
            .frame(width: 120, height: 120)
    }
}
