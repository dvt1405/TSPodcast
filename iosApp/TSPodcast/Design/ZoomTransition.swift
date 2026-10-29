import SwiftUI

/// Home → Detail zoom transition (features-spec §5 "shared element", Android's
/// `SharedTransitionLayout` between the cover and Detail). iOS 18+ only; iOS 17 keeps the
/// default push. A nil id also means a plain push.
extension View {
    /// Marks the view (a cover) as the source the destination zooms out of.
    @ViewBuilder
    func zoomTransitionSource(id: String?, in namespace: Namespace.ID) -> some View {
        if #available(iOS 18.0, *), let id {
            matchedTransitionSource(id: id, in: namespace)
        } else {
            self
        }
    }

    /// Applied to a pushed destination: it zooms from the source with the same id.
    @ViewBuilder
    func zoomTransition(sourceID: String?, in namespace: Namespace.ID) -> some View {
        if #available(iOS 18.0, *), let sourceID {
            navigationTransition(.zoom(sourceID: sourceID, in: namespace))
        } else {
            self
        }
    }
}
