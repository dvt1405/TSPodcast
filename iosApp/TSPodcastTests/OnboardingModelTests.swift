import SwiftUI
import XCTest
@preconcurrency import Shared
@testable import TSPodcast

/// Records analytics calls instead of logging them.
final class RecordingAnalyticsTracker: NSObject, AnalyticsTracker, @unchecked Sendable {
    private(set) var screens: [(screen: String, params: [String: String])] = []
    private(set) var events: [(name: String, screen: String)] = []

    func trackScreen(screen: String, params: [String: String]) {
        screens.append((screen, params))
    }

    func trackEvent(name: String, screen: String) {
        events.append((name, screen))
    }
}

/// M1-AC11: page analytics, the last-page button and the finished flag (`OnboardingViewModel`).
@MainActor
final class OnboardingModelTests: XCTestCase {
    func testFourPagesWithTheAndroidAssetsAndBackgrounds() {
        let pages = OnboardingPage.all
        XCTAssertEqual(pages.map(\.imageName), ["onboarding_slide_2", "onboarding_slide_3", "onboarding_slide_5", "onboarding_slide_6"])
        XCTAssertEqual(pages.map(\.index), [0, 1, 2, 3])
        for page in pages {
            XCTAssertNotNil(UIImage(named: page.imageName), page.imageName)
        }
        guard case .color(let first) = pages[0].background,
              case .gradient(let top2, let bottom2) = pages[1].background,
              case .gradient(let top3, let bottom3) = pages[2].background,
              case .color(let last) = pages[3].background else {
            return XCTFail("backgrounds differ from Android")
        }
        XCTAssertEqual(first, RGB(0xEAF0E4))
        XCTAssertEqual([top2, bottom2], [RGB(0x88BCCF), RGB(0xC8EAEE)])
        XCTAssertEqual([top3, bottom3], [RGB(0xEF8C77), RGB(0xEE8D78)])
        XCTAssertEqual(last, RGB(0xE0F8F8))
    }

    func testVietnameseTitlesHighlightTheAndroidPhrases() throws {
        let path = try XCTUnwrap(Bundle.main.path(forResource: "vi", ofType: "lproj"))
        let vi = try XCTUnwrap(Bundle(path: path))
        let expected: [(String, [String])] = [
            ("Khám Phá Podcast Yêu Thích Của Bạn", ["Podcast", "Của Bạn"]),
            ("Nội Dung Dành Riêng Cho Bạn", ["Riêng Cho Bạn"]),
            ("Nghe Mọi Lúc, Mọi Nơi", ["Mọi Lúc, Mọi Nơi"]),
            ("Sắp Xếp Và Cập Nhật Liên Tục", ["Sắp Xếp Và Cập Nhật"]),
        ]
        for (page, (title, phrases)) in zip(OnboardingPage.all, expected) {
            let attributed = page.title(bundle: vi, locale: Locale(identifier: "vi"))
            XCTAssertEqual(String(attributed.characters), title)
            XCTAssertEqual(highlightedRuns(attributed), phrases, title)
        }
    }

    func testHighlightIgnoresAPhraseMissingFromTheTitle() {
        let attributed = OnboardingPage.highlighted("Nghe Mọi Lúc", phrases: ["Không có", "", "Nghe"], color: .blue)
        XCTAssertEqual(highlightedRuns(attributed), ["Nghe"])
    }

    func testTracksEveryPageChangeOnceWithOneBasedPage() {
        let tracker = RecordingAnalyticsTracker()
        let model = OnboardingModel(analytics: tracker) {}

        model.pageDidChange(to: 0)
        model.pageDidChange(to: 0)
        model.pageDidChange(to: 1)
        model.pageDidChange(to: 2)
        model.pageDidChange(to: 1)
        model.pageDidChange(to: 3)
        model.pageDidChange(to: 7)

        XCTAssertEqual(tracker.screens.map(\.screen), Array(repeating: "Onboarding", count: 5))
        XCTAssertEqual(tracker.screens.map { $0.params["CurrentPage"] }, ["1", "2", "3", "2", "4"])
        XCTAssertTrue(tracker.screens.allSatisfy { $0.params.keys.sorted() == ["CurrentPage"] })
        XCTAssertTrue(tracker.events.isEmpty)
    }

    func testContinueOnlyOnTheLastPage() {
        let model = OnboardingModel(analytics: RecordingAnalyticsTracker()) {}
        for page in 0..<3 {
            model.currentPage = page
            XCTAssertFalse(model.showsContinue, "page \(page)")
        }
        model.currentPage = 3
        XCTAssertTrue(model.showsContinue)
    }

    func testFinishSavesTheFlagThenTracksOnce() async {
        let tracker = RecordingAnalyticsTracker()
        var saves = 0
        let model = OnboardingModel(analytics: tracker) {
            XCTAssertTrue(tracker.events.isEmpty, "the flag is saved before the event")
            saves += 1
        }

        let finished = await model.finish()
        let again = await model.finish()

        XCTAssertTrue(finished)
        XCTAssertFalse(again, "a second tap does not save or track again")
        XCTAssertEqual(saves, 1)
        XCTAssertEqual(tracker.events.map(\.name), ["OnboardingFinished"])
        XCTAssertEqual(tracker.events.map(\.screen), ["Onboarding"])
    }

    func testFailedSaveDoesNotFinishOrTrack() async {
        struct SaveError: Error {}
        let tracker = RecordingAnalyticsTracker()
        let model = OnboardingModel(analytics: tracker) { throw SaveError() }

        let finished = await model.finish()

        XCTAssertFalse(finished)
        XCTAssertFalse(model.isFinishing, "the user can tap again")
        XCTAssertTrue(tracker.events.isEmpty)
    }

    private func highlightedRuns(_ attributed: AttributedString) -> [String] {
        attributed.runs
            .filter { $0.foregroundColor != nil }
            .map { String(attributed[$0.range].characters) }
    }
}
