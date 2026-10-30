import XCTest
@testable import TSPodcast

/// M5-AC3: open/close rule of the Up next panel (Android `SlideArea.flingSlideAreaState`:
/// 40 % up opens, 20 % down closes, a fling of 1000 pt/s follows its direction).
final class UpNextPanelStateTests: XCTestCase {
    private func state(_ mode: UpNextPanelState.Mode, travel: CGFloat = 400) -> UpNextPanelState {
        UpNextPanelState(mode: mode, travel: travel)
    }

    func testDefaultIsCollapsed() {
        XCTAssertEqual(UpNextPanelState().mode, .collapsed)
        XCTAssertFalse(UpNextPanelState().isExpanded)
    }

    func testDragUpFortyPercentExpands() {
        var panel = state(.collapsed)
        panel.endDrag(translation: -160, velocity: 0)
        XCTAssertEqual(panel.mode, .expanded)
    }

    func testDragUpLessThanFortyPercentStaysCollapsed() {
        var panel = state(.collapsed)
        panel.endDrag(translation: -150, velocity: 0)
        XCTAssertEqual(panel.mode, .collapsed)
    }

    func testDragDownTwentyPercentCollapses() {
        var panel = state(.expanded)
        panel.endDrag(translation: 80, velocity: 0)
        XCTAssertEqual(panel.mode, .collapsed)
    }

    func testDragDownLessThanTwentyPercentStaysExpanded() {
        var panel = state(.expanded)
        panel.endDrag(translation: 70, velocity: 0)
        XCTAssertEqual(panel.mode, .expanded)
    }

    func testFlingUpExpandsAfterAShortDrag() {
        var panel = state(.collapsed)
        panel.endDrag(translation: -20, velocity: -1200)
        XCTAssertEqual(panel.mode, .expanded)
    }

    func testFlingDownCollapses() {
        var panel = state(.expanded)
        panel.endDrag(translation: -10, velocity: 1200)
        XCTAssertEqual(panel.mode, .collapsed)

        var still = state(.expanded)
        still.endDrag(translation: 0, velocity: 1200)
        XCTAssertEqual(still.mode, .collapsed)
    }

    func testVelocityDirectionWinsOverDistance() {
        var panel = state(.collapsed)
        panel.endDrag(translation: -300, velocity: 1200)
        XCTAssertEqual(panel.mode, .collapsed)

        var open = state(.expanded)
        open.endDrag(translation: 300, velocity: -1200)
        XCTAssertEqual(open.mode, .expanded)
    }

    func testFlingThresholdIsInclusive() {
        var panel = state(.collapsed)
        panel.endDrag(translation: 0, velocity: -1000)
        XCTAssertEqual(panel.mode, .expanded)

        var slow = state(.collapsed)
        slow.endDrag(translation: 0, velocity: -999)
        XCTAssertEqual(slow.mode, .collapsed)
    }

    func testZeroTravelDoesNotCrashAndOnlyVelocityChangesMode() {
        var panel = state(.collapsed, travel: 0)
        XCTAssertEqual(panel.offset(forDrag: -100), 0)
        XCTAssertEqual(panel.offset(forDrag: 100), 0)
        panel.endDrag(translation: -500, velocity: 0)
        XCTAssertEqual(panel.mode, .collapsed)
        panel.endDrag(translation: 0, velocity: -1500)
        XCTAssertEqual(panel.mode, .expanded)
        panel.endDrag(translation: 500, velocity: 0)
        XCTAssertEqual(panel.mode, .expanded)
        panel.endDrag(translation: 0, velocity: 1500)
        XCTAssertEqual(panel.mode, .collapsed)

        var negative = state(.expanded, travel: -20)
        XCTAssertEqual(negative.offset(forDrag: 50), 0)
        negative.endDrag(translation: 50, velocity: 0)
        XCTAssertEqual(negative.mode, .expanded)
    }

    func testOffsetClampsWhenCollapsed() {
        let panel = state(.collapsed)
        XCTAssertEqual(panel.offset(forDrag: 0), 400)
        XCTAssertEqual(panel.offset(forDrag: -1000), 0)
        XCTAssertEqual(panel.offset(forDrag: 50), 400)
        XCTAssertEqual(panel.offset(forDrag: -100), 300)
    }

    func testOffsetClampsWhenExpanded() {
        let panel = state(.expanded)
        XCTAssertEqual(panel.offset(forDrag: -50), 0)
        XCTAssertEqual(panel.offset(forDrag: 1000), 400)
        XCTAssertEqual(panel.offset(forDrag: 120), 120)
    }

    func testButtonsAndSelection() {
        var panel = UpNextPanelState()
        panel.expand()
        XCTAssertEqual(panel.mode, .expanded)
        panel.toggle()
        XCTAssertEqual(panel.mode, .collapsed)
        panel.toggle()
        XCTAssertEqual(panel.mode, .expanded)
        panel.didSelectItem()
        XCTAssertEqual(panel.mode, .collapsed)
        panel.expand()
        panel.collapse()
        XCTAssertEqual(panel.mode, .collapsed)
    }

    func testQueueChange() {
        var panel = state(.expanded)
        panel.queueDidChange(count: 3)
        XCTAssertEqual(panel.mode, .expanded)
        panel.queueDidChange(count: 0)
        XCTAssertEqual(panel.mode, .collapsed)

        var collapsed = state(.collapsed)
        collapsed.queueDidChange(count: 3)
        XCTAssertEqual(collapsed.mode, .collapsed)
    }
}
