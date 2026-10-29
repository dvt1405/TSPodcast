import SwiftUI

/// Where the MiniPlayer goes (features-spec §3.3, §3.7): above the tab bar, on every tab.
///
/// - iOS 26: `tabViewBottomAccessory` on the `TabView` (Liquid Glass capsule over the tab bar).
///   26.1+ uses the `isEnabled:` variant; on 26.0 the modifier is only attached while something
///   plays, so no empty capsule shows.
/// - iOS 17–18: `safeAreaInset(edge: .bottom)` on each tab's content.
///
/// Nothing plays in M1, so the slot is empty unless the Debug hook `-uitest-miniplayer-placeholder`
/// asks for a fake bar (`miniplayer.slot`). The Player milestone puts the real MiniPlayer here.
struct MiniPlayerSlot: View {
    var body: some View {
        #if DEBUG
        MiniPlayerPlaceholder()
        #else
        EmptyView()
        #endif
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

#if DEBUG
/// Fake MiniPlayer for XCUITest (M1-AC15): a bar the size of the real one.
private struct MiniPlayerPlaceholder: View {
    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(Color.brandPrimary)
                .frame(width: 36, height: 36)
            Text(verbatim: "MiniPlayer")
                .font(.subheadline.weight(.semibold))
            Spacer(minLength: 0)
            Image(systemName: "play.fill")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("miniplayer.slot")
    }
}
#endif
