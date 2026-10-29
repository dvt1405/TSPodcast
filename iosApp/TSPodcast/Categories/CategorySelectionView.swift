import SwiftUI

/// Favourite category selection (features-spec §3.2). M1 T2 scaffold: title and description
/// only, so `RootView` can route here; T3 adds the grid, badges and "Lưu và tiếp tục".
struct CategorySelectionView: View {
    let onSaved: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                Text("category.title")
                    .font(.title2.bold())
                Text("category.subtitle")
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .accessibilityIdentifier("category.grid")
    }
}
