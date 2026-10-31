import SwiftUI

/// Brand colours from `Resources/Assets.xcassets/Colors` (Android `sharedLibrary` `Colors`, with
/// dark variants added for iOS). Body text uses the system semantic styles (`.primary`,
/// `.secondary`) so it follows Light/Dark and Dynamic Type.
extension Color {
    /// `#184BB4` (dark `#6E97F0`).
    static let brandPrimary = Color("Primary")
    /// `#4A7DE7` (dark `#8AABF2`).
    static let brandSecondary = Color("Secondary")
    /// `#39375B` (dark `#E4E3F5`), titles such as the onboarding headings.
    static let textTitle = Color("TextTitle")
    /// `#007AFF` at 15 % (Android `StrokeColor`), borders of category cells.
    static let stroke = Color("Stroke")
    /// Cam San Hô `#FF7A59` (same in Dark), brand accent (BRAND §2). Decoration only: the 6 pt dot
    /// next to "Đang phát" in Up next. Never a text colour or a background behind text (contrast).
    static let brandAccent = Color("BrandAccent")
}
