import SwiftUI

/// One onboarding page, `OnboardingViewModel.defaultItems` on Android: illustration, background
/// (plain colour or vertical gradient) and a title whose highlight phrases are drawn in Primary.
///
/// The page colours are Android's fixed values in Light and Dark Mode alike: the illustrations
/// are painted on these backgrounds (M1 §4.3).
struct OnboardingPage: Identifiable, Sendable {
    enum Background: Sendable {
        case color(RGB)
        /// Top to bottom. Android repeats each stop (3 × top, 2 × bottom), which puts the
        /// blend between 50 % and 75 % of the height.
        case gradient(top: RGB, bottom: RGB)
    }

    let index: Int
    let imageName: String
    let background: Background
    let titleKey: String
    let highlightKeys: [String]

    var id: Int { index }

    static let all: [OnboardingPage] = [
        OnboardingPage(
            index: 0,
            imageName: "onboarding_slide_2",
            background: .color(RGB(0xEAF0E4)),
            titleKey: "onboarding.title.1",
            highlightKeys: ["onboarding.title.1.highlight.1", "onboarding.title.1.highlight.2"]
        ),
        OnboardingPage(
            index: 1,
            imageName: "onboarding_slide_3",
            background: .gradient(top: RGB(0x88BCCF), bottom: RGB(0xC8EAEE)),
            titleKey: "onboarding.title.2",
            highlightKeys: ["onboarding.title.2.highlight.1"]
        ),
        OnboardingPage(
            index: 2,
            imageName: "onboarding_slide_5",
            background: .gradient(top: RGB(0xEF8C77), bottom: RGB(0xEE8D78)),
            titleKey: "onboarding.title.3",
            highlightKeys: ["onboarding.title.3.highlight.1"]
        ),
        OnboardingPage(
            index: 3,
            imageName: "onboarding_slide_6",
            background: .color(RGB(0xE0F8F8)),
            titleKey: "onboarding.title.4",
            highlightKeys: ["onboarding.title.4.highlight.1"]
        ),
    ]

    /// Title colour `#39375B` (Android hardcodes it on the page).
    static let titleColor = RGB(0x39375B)
    /// Highlight colour, `Colors.Primary` `#184BB4`.
    static let highlightColor = RGB(0x184BB4)

    /// Localised title with every highlight phrase coloured, like the `buildAnnotatedString` of
    /// `OnboardingScreen`. A phrase that does not occur in the title is ignored (Android would
    /// style a wrong range with `indexOf == -1`).
    func title(bundle: Bundle = .main, locale: Locale = .current) -> AttributedString {
        let text = Self.localized(titleKey, bundle: bundle, locale: locale)
        let highlights = highlightKeys.map { Self.localized($0, bundle: bundle, locale: locale) }
        return Self.highlighted(text, phrases: highlights, color: Self.highlightColor.color)
    }

    static func highlighted(_ text: String, phrases: [String], color: Color) -> AttributedString {
        var attributed = AttributedString(text)
        for phrase in phrases where !phrase.isEmpty {
            if let range = attributed.range(of: phrase) {
                attributed[range].foregroundColor = color
            }
        }
        return attributed
    }

    private static func localized(_ key: String, bundle: Bundle, locale: Locale) -> String {
        String(localized: String.LocalizationValue(key), bundle: bundle, locale: locale)
    }
}

/// A fixed sRGB colour from a `0xRRGGBB` literal.
struct RGB: Equatable, Sendable {
    let hex: UInt32

    init(_ hex: UInt32) {
        self.hex = hex
    }

    var color: Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
