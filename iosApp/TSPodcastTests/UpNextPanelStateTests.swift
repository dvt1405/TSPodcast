import XCTest
@testable import TSPodcast

/// M5-AC3: mode of the Up next sheet and the cross-fade of its peek rows and list. The drag
/// rules (thresholds, flings, scroll hand-off) are the system sheet's, covered by
/// `UpNextPanelUITests` on a device.
final class UpNextPanelStateTests: XCTestCase {
    private func state(_ mode: UpNextPanelState.Mode) -> UpNextPanelState {
        UpNextPanelState(mode: mode)
    }

    func testDefaultIsCollapsed() {
        XCTAssertEqual(UpNextPanelState().mode, .collapsed)
        XCTAssertFalse(UpNextPanelState().isExpanded)
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

    // MARK: - Progress (the peek rows and the list cross-fade with the sheet position)

    func testProgressFollowsTheSheetTop() {
        let panel = state(.collapsed)
        XCTAssertEqual(panel.progress(top: 716, peekTop: 716, openTop: 91), 0)
        XCTAssertEqual(panel.progress(top: 559.75, peekTop: 716, openTop: 91), 0.25)
        XCTAssertEqual(panel.progress(top: 91, peekTop: 716, openTop: 91), 1)
        XCTAssertEqual(panel.progress(top: 40, peekTop: 716, openTop: 91), 1, "clamped above the open top")
        XCTAssertEqual(panel.progress(top: 800, peekTop: 716, openTop: 91), 0, "clamped below the peek")
        XCTAssertEqual(state(.expanded).progress(top: 403.5, peekTop: 716, openTop: 91), 0.5, "the position decides, not the mode")
    }

    func testProgressWithoutMeasuredTargetsIsTheMode() {
        XCTAssertEqual(state(.collapsed).progress(top: 300, peekTop: 0, openTop: 0), 0)
        XCTAssertEqual(state(.expanded).progress(top: 300, peekTop: 0, openTop: 0), 1)
        XCTAssertEqual(state(.expanded).progress(top: 300, peekTop: 91, openTop: 716), 1)
        XCTAssertEqual(state(.collapsed).progress(top: 300, peekTop: 91, openTop: 716), 0)
    }

    func testPeekRowsAndListNeverOverlap() {
        XCTAssertEqual(UpNextPanelState.peekRowsOpacity(progress: 0), 1)
        XCTAssertEqual(UpNextPanelState.listOpacity(progress: 0), 0)
        XCTAssertEqual(UpNextPanelState.peekRowsOpacity(progress: 0.25), 0.5)
        XCTAssertEqual(UpNextPanelState.listOpacity(progress: 0.25), 0)
        XCTAssertEqual(UpNextPanelState.peekRowsOpacity(progress: 0.75), 0)
        XCTAssertEqual(UpNextPanelState.listOpacity(progress: 0.75), 0.5)
        XCTAssertEqual(UpNextPanelState.peekRowsOpacity(progress: 1), 0)
        XCTAssertEqual(UpNextPanelState.listOpacity(progress: 1), 1)
        for step in 0...20 {
            let progress = CGFloat(step) / 20
            XCTAssertTrue(
                UpNextPanelState.peekRowsOpacity(progress: progress) == 0 || UpNextPanelState.listOpacity(progress: progress) == 0,
                "both visible at \(progress)"
            )
        }
    }

    /// The rapid-toggle path: any number of taps leaves a mode that matches their parity.
    func testRepeatedTogglesAlternate() {
        var panel = state(.collapsed)
        for tap in 1...11 {
            panel.toggle()
            XCTAssertEqual(panel.isExpanded, tap % 2 == 1, "after \(tap) taps")
        }
    }

    // MARK: - Detent correction (the sheet tops land on their targets)

    func testDetentCorrectionAddsTheMissOnce() {
        var correction = UpNextDetentCorrection()
        correction.layoutDidChange(columnHeight: 845)
        correction.settled(top: 114, target: 91, isOpen: true)
        XCTAssertEqual(correction.open, 23, "open top 23 pt low → open detent 23 pt taller")
        correction.settled(top: 92, target: 91, isOpen: true)
        XCTAssertEqual(correction.open, 23, "measured once per layout")
        correction.settled(top: 714, target: 716, isOpen: false)
        XCTAssertEqual(correction.peek, -2, "peek top 2 pt high → peek detent 2 pt shorter")
    }

    func testDetentCorrectionIgnoresASheetHeldMidDrag() {
        var correction = UpNextDetentCorrection()
        correction.settled(top: 400, target: 91, isOpen: true)
        correction.settled(top: 400, target: 716, isOpen: false)
        XCTAssertEqual(correction.open, 0)
        XCTAssertEqual(correction.peek, 0)
        correction.settled(top: 100, target: 91, isOpen: true)
        XCTAssertEqual(correction.open, 9, "a real settle afterwards still counts")
    }

    func testDetentCorrectionMeasuresAgainAfterALayoutChange() {
        var correction = UpNextDetentCorrection()
        correction.layoutDidChange(columnHeight: 845)
        correction.settled(top: 114, target: 91, isOpen: true)
        correction.layoutDidChange(columnHeight: 845.2)
        correction.settled(top: 95, target: 91, isOpen: true)
        XCTAssertEqual(correction.open, 23, "same layout")
        correction.layoutDidChange(columnHeight: 700)
        correction.settled(top: 95, target: 91, isOpen: true)
        XCTAssertEqual(correction.open, 27, "a new layout adds its miss to the old offset")
        var unmeasured = UpNextDetentCorrection()
        unmeasured.settled(top: 30, target: 0, isOpen: true)
        XCTAssertEqual(unmeasured.open, 0, "no target yet")
    }
}
