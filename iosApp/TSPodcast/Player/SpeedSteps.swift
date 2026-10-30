import Foundation

/// Speed values of the Player and its speed sheet, in **tenths** like shared `PlaybackSpeed`
/// (M6 §3.2): 0.5×–3.0×, step 0.1. Kept in plain Swift so the mapping is unit-tested on macOS;
/// `SpeedStepsTests` checks the constants against the shared ones on the device.
enum SpeedSteps {
    static let minTenths = 5
    static let maxTenths = 30
    static let defaultTenths = 10
    /// The chips of the sheet (shared `PlaybackSpeed.CHIPS`).
    static let chips = [8, 10, 12, 15, 20]
    /// `changePlaybackRateCommand.supportedPlaybackRates` (shared `PlaybackSpeed.REMOTE_RATES`).
    static let remoteRates: [Double] = [0.5, 0.8, 1.0, 1.2, 1.5, 2.0, 2.5, 3.0]

    static func clamp(_ tenths: Int) -> Int {
        min(max(tenths, minTenths), maxTenths)
    }

    /// The slider (0.5...3.0) to tenths, rounded to the nearest step.
    static func tenths(sliderValue: Double) -> Int {
        guard sliderValue.isFinite else { return defaultTenths }
        return clamp(Int((sliderValue * 10).rounded()))
    }

    static func sliderValue(tenths: Int) -> Double {
        Double(clamp(tenths)) / 10
    }

    static func decreased(_ tenths: Int) -> Int { clamp(tenths - 1) }
    static func increased(_ tenths: Int) -> Int { clamp(tenths + 1) }
    static func canDecrease(_ tenths: Int) -> Bool { tenths > minTenths }
    static func canIncrease(_ tenths: Int) -> Bool { tenths < maxTenths }

    /// "1.5×" (shared `PlaybackSpeed.label`): the button and the big number of the sheet.
    static func label(_ tenths: Int) -> String {
        let value = clamp(tenths)
        return "\(value / 10).\(value % 10)×"
    }

    /// VoiceOver value in the app language: "1,5×" in Vietnamese, "1.5×" in English.
    static func spokenLabel(_ tenths: Int, locale: Locale) -> String {
        let value = Double(clamp(tenths)) / 10
        return value.formatted(.number.precision(.fractionLength(1)).locale(locale)) + "×"
    }
}
