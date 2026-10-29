import SwiftUI

/// Root of the "Tìm kiếm" tab. M1 only has the title; the Search milestone adds E7/E8 search
/// and the category chips (features-spec §3.9).
struct SearchRootView: View {
    var body: some View {
        ScrollView {
            Color.clear
                .frame(height: 1)
                .accessibilityHidden(true)
        }
        .accessibilityIdentifier("search.root")
    }
}
