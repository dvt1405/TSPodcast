import XCTest
@testable import TSPodcast

/// M5-AC8: the MiniPlayer turns compact when the iOS 26 accessory is inline (tab bar minimized)
/// or at an accessibility text size (plan §3.3/§3.4).
final class MiniPlayerLayoutTests: XCTestCase {
    func testRegularByDefault() {
        XCTAssertEqual(MiniPlayerLayout.resolve(isInlineAccessory: false, isAccessibilitySize: false), .regular)
    }

    func testCompactWhenInline() {
        XCTAssertEqual(MiniPlayerLayout.resolve(isInlineAccessory: true, isAccessibilitySize: false), .compact)
    }

    func testCompactAtAnAccessibilitySize() {
        XCTAssertEqual(MiniPlayerLayout.resolve(isInlineAccessory: false, isAccessibilitySize: true), .compact)
    }

    func testCompactWhenInlineAtAnAccessibilitySize() {
        XCTAssertEqual(MiniPlayerLayout.resolve(isInlineAccessory: true, isAccessibilitySize: true), .compact)
    }
}
