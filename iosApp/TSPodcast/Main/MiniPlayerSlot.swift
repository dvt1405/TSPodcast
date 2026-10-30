import SwiftUI

/// Which MiniPlayer layout to draw (M5 §3.3/§3.4); pure so it is unit tested
/// (`MiniPlayerLayoutTests`).
enum MiniPlayerLayout: Equatable, Sendable {
    /// Artwork 36, title + podcast name, play/pause, thin progress line (M3).
    case regular
    /// Artwork 28, title only, play/pause; no podcast name and no progress line.
    case compact

    /// Compact when the iOS 26 accessory sits inline in the minimized tab bar, or at an
    /// accessibility Dynamic Type size (the two-line layout would not fit the slot).
    static func resolve(isInlineAccessory: Bool, isAccessibilitySize: Bool) -> MiniPlayerLayout {
        isInlineAccessory || isAccessibilitySize ? .compact : .regular
    }
}

/// Where the MiniPlayer goes (features-spec §3.3, §3.7; Android `MainActivity.MiniPlayer` /
/// `PlayerWidgetMain`): above the tab bar, on every tab.
///
/// - iOS 26: `tabViewBottomAccessory` on the `TabView` (the system Liquid Glass capsule over the
///   tab bar). 26.1+ uses the `isEnabled:` variant; on 26.0 the modifier is only attached while
///   something plays, so no empty capsule shows. When the tab bar minimizes on scroll the accessory
///   moves inline (`tabViewBottomAccessoryPlacement == .inline`) and the layout turns compact.
/// - iOS 17–18: `safeAreaInset(edge: .bottom)` on each tab's content, as a floating capsule
///   (`glassSurface` → `.regularMaterial`, 12 pt from the sides, a soft shadow).
///
/// It shows while the player holds an item and the Player is closed (`MainTabView`). Together with
/// `Design/Glass.swift` this is the only file that calls iOS 26 APIs (M5 §3.4).
struct MiniPlayerSlot: View {
    /// iOS 26: the accessory sits inline in the minimized tab bar.
    var isInlineAccessory = false

    @Environment(AppEnvironment.self) private var environment
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        MiniPlayerView(
            engine: environment.audioEngine,
            layout: .resolve(
                isInlineAccessory: isInlineAccessory,
                isAccessibilitySize: dynamicTypeSize.isAccessibilitySize
            )
        ) {
            AppLog.playback.notice("miniplayer_open")
            environment.isPlayerPresented = true
        }
    }
}

/// iOS 26 accessory content: reads the accessory placement. It is an iOS 26-only environment
/// value, so it lives in its own view instead of a stored property of `MiniPlayerSlot`.
@available(iOS 26.0, *)
private struct AccessoryMiniPlayer: View {
    @Environment(\.tabViewBottomAccessoryPlacement) private var placement

    var body: some View {
        MiniPlayerSlot(isInlineAccessory: placement == .inline)
    }
}

extension View {
    /// iOS 26 placement; apply to the `TabView`.
    @ViewBuilder
    func miniPlayerAccessory(isPresented: Bool) -> some View {
        if #available(iOS 26.1, *) {
            tabViewBottomAccessory(isEnabled: isPresented) { AccessoryMiniPlayer() }
        } else if #available(iOS 26.0, *) {
            if isPresented {
                tabViewBottomAccessory { AccessoryMiniPlayer() }
            } else {
                self
            }
        } else {
            self
        }
    }

    /// iOS 17–18 placement; apply to each tab's content (its `NavigationStack`). A floating
    /// Material capsule instead of the M3 edge-to-edge `.bar` strip.
    @ViewBuilder
    func miniPlayerInset(isPresented: Bool) -> some View {
        if #available(iOS 26.0, *) {
            self
        } else {
            safeAreaInset(edge: .bottom, spacing: 0) {
                if isPresented {
                    MiniPlayerSlot()
                        .glassSurface(in: Capsule())
                        .shadow(color: .black.opacity(0.12), radius: 8, y: 2)
                        .padding(.horizontal, 12)
                        .padding(.bottom, 8)
                }
            }
        }
    }
}
