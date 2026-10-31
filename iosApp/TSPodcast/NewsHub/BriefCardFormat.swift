import Foundation

/// Texts of the "Điểm tin sáng" card (M6 §3.4), kept free of SwiftUI and Kotlin so they are tested
/// on macOS too: the day on the card ("Thứ Năm 29/10", "Thursday, Oct 29") and the total minutes.
///
/// The day is the device's date and time zone, in the app's language (not the region format):
/// a Vietnamese UI always reads "Thứ Năm 29/10", an English one "Thursday, Oct 29".
enum BriefCardFormat {
    /// Locale of the language the app shows (`vi`, the development language, or `en`).
    static var appLocale: Locale {
        Locale(identifier: Bundle.main.preferredLocalizations.first ?? "vi")
    }

    /// Weekday and day/month: `EEEE dd/MM` in Vietnamese, the locale's own `EEEE MMM d` elsewhere.
    static func day(_ date: Date, locale: Locale, timeZone: TimeZone) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        calendar.locale = locale
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = locale
        formatter.timeZone = timeZone
        if locale.language.languageCode == .vietnamese {
            formatter.dateFormat = "EEEE dd/MM"
        } else {
            formatter.setLocalizedDateFormatFromTemplate("EEEEMMMd")
        }
        return formatter.string(from: date)
    }

    /// Total minutes, rounded **up** (1 380 s → 23, 1 381 s → 24); 0 when the feeds give none.
    static func minutes(totalSeconds: Int64) -> Int {
        guard totalSeconds > 0 else { return 0 }
        return Int((totalSeconds + 59) / 60)
    }

    /// "5 tập · 23 phút": both parts already localized (plural rules live in the strings table).
    static func summary(episodes: String, minutes: String) -> String {
        "\(episodes) · \(minutes)"
    }
}
