import SwiftUI

extension View {
    /// Long-press "Ẩn show này" on a Home card/row or a Search result (M6 §3.5). Hiding drops the
    /// show from every screen showing it (`HiddenShowsModel.onHide`); Settings → "Show đã ẩn"
    /// undoes it, so there is no confirmation here (the Detail menu has one).
    func hideShowContextMenu(feedId: Int64, title: String, imageURL: String?, screen: String) -> some View {
        modifier(HideShowContextMenu(feedId: feedId, title: title, imageURL: imageURL, screen: screen))
    }
}

private struct HideShowContextMenu: ViewModifier {
    let feedId: Int64
    let title: String
    let imageURL: String?
    let screen: String

    @Environment(AppEnvironment.self) private var environment

    func body(content: Content) -> some View {
        content.contextMenu {
            Button {
                let hiddenShows = environment.hiddenShows
                Task { await hiddenShows.hide(feedId: feedId, title: title, imageURL: imageURL, screen: screen) }
            } label: {
                Label("show.hide", systemImage: "eye.slash")
            }
            .accessibilityIdentifier("show.hide")
        }
    }
}
