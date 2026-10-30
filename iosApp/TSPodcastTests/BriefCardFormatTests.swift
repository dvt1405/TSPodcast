import XCTest
@testable import TSPodcast

/// M6-AC17: the texts of the morning brief card. Pure Swift (Foundation only), also run with
/// `swift test` on macOS. The templates are the `news.brief.*` values of the strings table.
final class BriefCardFormatTests: XCTestCase {
    private let vietnam = TimeZone(identifier: "Asia/Ho_Chi_Minh")!

    private func date(_ day: Int, hour: Int = 7) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = vietnam
        return calendar.date(from: DateComponents(year: 2026, month: 10, day: day, hour: hour))!
    }

    func testVietnameseDayIsWeekdayAndDayMonth() {
        let vi = Locale(identifier: "vi")
        // 29/10/2026 is a Thursday (the plan's "Thứ Năm 30/10" mixes the two days up).
        XCTAssertEqual(BriefCardFormat.day(date(29), locale: vi, timeZone: vietnam), "Thứ Năm 29/10")
        XCTAssertEqual(BriefCardFormat.day(date(30), locale: vi, timeZone: vietnam), "Thứ Sáu 30/10")
        XCTAssertEqual(
            String(format: "Điểm tin sáng · %@", BriefCardFormat.day(date(29), locale: vi, timeZone: vietnam)),
            "Điểm tin sáng · Thứ Năm 29/10"
        )
    }

    func testEnglishDayUsesTheLocaleOrder() {
        let en = Locale(identifier: "en")
        XCTAssertEqual(BriefCardFormat.day(date(29), locale: en, timeZone: vietnam), "Thursday, Oct 29")
        XCTAssertEqual(
            String(format: "Morning brief · %@", BriefCardFormat.day(date(29), locale: en, timeZone: vietnam)),
            "Morning brief · Thursday, Oct 29"
        )
    }

    func testDayFollowsTheDeviceTimeZone() {
        // Midnight of the 30th in Vietnam is 17:00 on the 29th in UTC.
        let vi = Locale(identifier: "vi")
        let utc = TimeZone(identifier: "UTC")!
        XCTAssertEqual(BriefCardFormat.day(date(30, hour: 0), locale: vi, timeZone: vietnam), "Thứ Sáu 30/10")
        XCTAssertEqual(BriefCardFormat.day(date(30, hour: 0), locale: vi, timeZone: utc), "Thứ Năm 29/10")
    }

    func testMinutesRoundUp() {
        XCTAssertEqual(BriefCardFormat.minutes(totalSeconds: 1380), 23)
        XCTAssertEqual(BriefCardFormat.minutes(totalSeconds: 1381), 24)
        XCTAssertEqual(BriefCardFormat.minutes(totalSeconds: 1), 1)
        XCTAssertEqual(BriefCardFormat.minutes(totalSeconds: 0), 0)
        XCTAssertEqual(BriefCardFormat.minutes(totalSeconds: -5), 0)
    }

    func testSummaryJoinsBothParts() {
        let episodes = String(format: "%lld tập", Int64(5))
        let minutes = String(format: "%lld phút", Int64(BriefCardFormat.minutes(totalSeconds: 4 * 300 + 180)))
        XCTAssertEqual(BriefCardFormat.summary(episodes: episodes, minutes: minutes), "5 tập · 23 phút")
    }
}
