import SwiftUI

/// Where the MiniPlayer goes (features-spec §3.3, §3.7): above the tab bar, on every tab.
///
/// - iOS 26: `tabViewBottomAccessory` on the `TabView` (Liquid Glass capsule over the tab bar).
///   26.1+ uses the `isEnabled:` variant; on 26.0 the modifier is only attached while something
///   plays, so no empty capsule shows.
/// - iOS 17–18: `safeAreaInset(edge: .bottom)` on each tab's content.
///
/// It shows while the player holds an item and the Player is closed (`MainTabView`).
struct MiniPlayerSlot: View {
    @Environment(AppEnvironment.self) private var environment

    var body: some View {
        MiniPlayerView(engine: environment.audioEngine) {
            AppLog.playback.notice("miniplayer_open")
            environment.isPlayerPresented = true
        }
    }
}

extension View {
    /// iOS 26 placement; apply to the `TabView`.
    @ViewBuilder
    func miniPlayerAccessory(isPresented: Bool) -> some View {
        if #available(iOS 26.1, *) {
            tabViewBottomAccessory(isEnabled: isPresented) { MiniPlayerSlot() }
        } else if #available(iOS 26.0, *) {
            if isPresented {
                tabViewBottomAccessory { MiniPlayerSlot() }
            } else {
                self
            }
        } else {
            self
        }
    }

    /// iOS 17–18 placement; apply to each tab's content (its `NavigationStack`).
    @ViewBuilder
    func miniPlayerInset(isPresented: Bool) -> some View {
        if #available(iOS 26.0, *) {
            self
        } else {
            safeAreaInset(edge: .bottom, spacing: 0) {
                if isPresented {
                    MiniPlayerSlot()
                        .background(.bar)
                }
            }
        }
    }
}
