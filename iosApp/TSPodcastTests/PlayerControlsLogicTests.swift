import XCTest
@testable import TSPodcast

/// M6-AC7: what the speed sheet and the sleep timer button compute (plain Swift; the same file
/// runs on macOS in the scratch package while the iOS platform is missing).
final class SpeedStepsTests: XCTestCase {
    func testSliderMapsToTenths() {
        XCTAssertEqual(SpeedSteps.tenths(sliderValue: 1.5), 15)
        XCTAssertEqual(SpeedSteps.tenths(sliderValue: 1.04), 10)
        XCTAssertEqual(SpeedSteps.tenths(sliderValue: 1.06), 11)
        XCTAssertEqual(SpeedSteps.tenths(sliderValue: 0.1), 5)
        XCTAssertEqual(SpeedSteps.tenths(sliderValue: 9), 30)
        XCTAssertEqual(SpeedSteps.tenths(sliderValue: .nan), 10)
        XCTAssertEqual(SpeedSteps.sliderValue(tenths: 15), 1.5)
        XCTAssertEqual(SpeedSteps.sliderValue(tenths: 99), 3.0)
    }

    func testMinusAndPlusStopAtTheEnds() {
        XCTAssertEqual(SpeedSteps.decreased(10), 9)
        XCTAssertEqual(SpeedSteps.increased(10), 11)
        XCTAssertEqual(SpeedSteps.decreased(5), 5)
        XCTAssertEqual(SpeedSteps.increased(30), 30)
        XCTAssertFalse(SpeedSteps.canDecrease(5))
        XCTAssertTrue(SpeedSteps.canDecrease(6))
        XCTAssertFalse(SpeedSteps.canIncrease(30))
        XCTAssertTrue(SpeedSteps.canIncrease(29))
    }

    func testChipsRatesAndLabels() {
        XCTAssertEqual(SpeedSteps.chips, [8, 10, 12, 15, 20])
        XCTAssertEqual(SpeedSteps.remoteRates, [0.5, 0.8, 1.0, 1.2, 1.5, 2.0, 2.5, 3.0])
        XCTAssertEqual(SpeedSteps.label(15), "1.5×")
        XCTAssertEqual(SpeedSteps.label(10), "1.0×")
        XCTAssertEqual(SpeedSteps.label(4), "0.5×")
        XCTAssertEqual(SpeedSteps.spokenLabel(15, locale: Locale(identifier: "vi")), "1,5×")
        XCTAssertEqual(SpeedSteps.spokenLabel(15, locale: Locale(identifier: "en")), "1.5×")
        XCTAssertEqual(SpeedSteps.spokenLabel(10, locale: Locale(identifier: "vi")), "1,0×")
    }
}

final class SleepTimerStatusTests: XCTestCase {
    func testSheetOptionsAndIdentifiers() {
        XCTAssertEqual(SleepTimerSetting.presetMinutes, [5, 10, 15, 30, 45, 60])
        XCTAssertEqual(
            SleepTimerSetting.sheetOptions.map(\.identifierSuffix),
            ["5", "10", "15", "30", "45", "60", "endOfEpisode", "off"]
        )
    }

    func testClampedMinutes() {
        XCTAssertEqual(SleepTimerSetting.clamped(minutes: 0), .minutes(1))
        XCTAssertEqual(SleepTimerSetting.clamped(minutes: 45), .minutes(45))
        XCTAssertEqual(SleepTimerSetting.clamped(minutes: 500), .minutes(180))
    }

    func testRemainingAndActive() {
        let now = Date(timeIntervalSince1970: 1_000)
        XCTAssertFalse(SleepTimerStatus().isActive)
        XCTAssertNil(SleepTimerStatus().remainingMs(at: now))
        let status = SleepTimerStatus(setting: .minutes(30), deadline: now.addingTimeInterval(1_694))
        XCTAssertTrue(status.isActive)
        XCTAssertEqual(status.remainingMs(at: now), 1_694_000)
        XCTAssertEqual(status.remainingMs(at: now.addingTimeInterval(2_000)), 0, "never negative")
        XCTAssertTrue(SleepTimerStatus(setting: .endOfEpisode, deadline: nil).isActive)
    }

    func testClockText() {
        XCTAssertEqual(SleepTimerStatus.clockText(remainingMs: 1_694_000), "28:14")
        XCTAssertEqual(SleepTimerStatus.clockText(remainingMs: 1_800_000), "30:00")
        XCTAssertEqual(SleepTimerStatus.clockText(remainingMs: 1_799_001), "30:00", "seconds round up")
        XCTAssertEqual(SleepTimerStatus.clockText(remainingMs: 9_000), "0:09")
        XCTAssertEqual(SleepTimerStatus.clockText(remainingMs: 1), "0:01")
        XCTAssertEqual(SleepTimerStatus.clockText(remainingMs: 0), "0:00")
        XCTAssertEqual(SleepTimerStatus.clockText(remainingMs: -5), "0:00")
        XCTAssertEqual(SleepTimerStatus.clockText(remainingMs: 3_723_000), "1:02:03")
        XCTAssertEqual(SleepTimerStatus.clockText(remainingMs: 10_800_000), "3:00:00")
    }
}
