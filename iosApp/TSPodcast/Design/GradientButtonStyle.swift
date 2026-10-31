import SwiftUI

/// Android `TSButton` / `TSRoundedButton` (`sharedLibrary/ui/widget/Button.kt`): 50 pt high, a
/// Primary → Secondary gradient and white text; disabled it turns `#DDE1E6` with `#343A3F` text.
/// `cornerRadius` 8 is `TSButton`, `.infinity` gives the capsule of `TSRoundedButton`. Android has
/// no dark theme; in Dark Mode the disabled state uses the system's dark fill instead.
struct GradientButtonStyle: ButtonStyle {
    var cornerRadius: CGFloat = 8
    var fillsWidth = false

    @Environment(\.isEnabled) private var isEnabled

    static let disabledBackground = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? .systemGray5
            : UIColor(red: 0xDD / 255, green: 0xE1 / 255, blue: 0xE6 / 255, alpha: 1)
    })
    static let disabledForeground = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? .secondaryLabel
            : UIColor(red: 0x34 / 255, green: 0x3A / 255, blue: 0x3F / 255, alpha: 1)
    })
    /// Fixed brand colours: the button looks the same on the light onboarding pages in Dark Mode.
    static let gradient = LinearGradient(
        colors: [
            Color(red: 0x18 / 255, green: 0x4B / 255, blue: 0xB4 / 255),
            Color(red: 0x4A / 255, green: 0x7D / 255, blue: 0xE7 / 255),
        ],
        startPoint: .leading,
        endPoint: .trailing
    )

    func makeBody(configuration: Configuration) -> some View {
        let shape = RoundedRectangle(cornerRadius: min(cornerRadius, 25), style: .continuous)
        configuration.label
            .font(.body.weight(.semibold))
            .lineLimit(1)
            .padding(.horizontal, 24)
            .frame(maxWidth: fillsWidth ? .infinity : nil, minHeight: 50)
            .foregroundStyle(isEnabled ? Color.white : Self.disabledForeground)
            .background {
                if isEnabled {
                    shape.fill(Self.gradient)
                } else {
                    shape.fill(Self.disabledBackground)
                }
            }
            .contentShape(shape)
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}
