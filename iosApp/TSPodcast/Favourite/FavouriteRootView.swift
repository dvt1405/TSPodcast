import SwiftUI

/// Root of the "Yêu thích" tab. Favourites are not stored yet (persistence milestone), so it is
/// always Android's empty state (`EmptyFavouriteWidget`): the illustration, "Không có chương
/// trình yêu thích." and "Khám phá thêm", which switches to the Discover tab.
struct FavouriteRootView: View {
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

#Preview {
    NavigationStack {
        FavouriteRootView {}
            .navigationTitle(Text("tab.favourite"))
    }
}
