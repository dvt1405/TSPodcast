import CoreGraphics
import Foundation

/// Open/closed state of the Up next panel on the Player (Android `SlideAreaState` + the fling
/// rule of `SlideArea.flingSlideAreaState`). Pure value type so it is unit tested; `PlayerView`
/// keeps it in `@State` and feeds `travel` from layout (M5 §3.2).
struct UpNextPanelState: Equatable, Sendable {
    enum Mode: Equatable, Sendable {
        /// The peek under the player controls (Android `Hidden`: the "UP NEXT" header).
        case collapsed
        /// The full list, top just below the close row (Android `Expanded`).
        case expanded
    }

    /// A drag faster than this (pt/s) decides by its direction alone (Android: 1000).
    static let flingVelocity: CGFloat = 1000

    var mode: Mode = .collapsed
    /// Distance (pt) between the panel top when collapsed and when expanded. `<= 0` until measured:
    /// then only the velocity rule, taps and buttons change the mode.
    var travel: CGFloat = 0

    var isExpanded: Bool { mode == .expanded }

    /// Offset of the panel top from its expanded position (0 = fully open, `travel` = peek) while
    /// dragging by `dy` (positive = down). Clamped to `0...travel`.
    func offset(forDrag dy: CGFloat) -> CGFloat {
        let travel = max(travel, 0)
        switch mode {
        case .collapsed:
            return travel + min(max(dy, -travel), 0)
        case .expanded:
            return min(max(dy, 0), travel)
        }
    }

    /// How far open the panel is while dragging by `dy`: 0 at the peek, 1 fully open. The peek
    /// rows fade out and the list fades in with it, so nothing jumps when the mode flips. Without
    /// a measured travel, the mode alone.
    func progress(forDrag dy: CGFloat) -> CGFloat {
        guard travel > 0 else { return isExpanded ? 1 : 0 }
        return 1 - offset(forDrag: dy) / travel
    }

    /// The peek rows fade out over the first half of the opening and the list fades in over the
    /// second half, so the two never show on top of each other.
    static func peekRowsOpacity(progress: CGFloat) -> CGFloat {
        min(max(1 - progress * 2, 0), 1)
    }

    static func listOpacity(progress: CGFloat) -> CGFloat {
        min(max(progress * 2 - 1, 0), 1)
    }

    /// Settles the mode when a drag of `dy` ends with velocity `vy` (pt/s, positive = down):
    /// a fling (|vy| ≥ 1000) follows its direction; otherwise it opens past 40 % up and closes past
    /// 20 % down (Android `anim.value <= -0.4 * initSize` / `>= -0.8 * initSize`).
    mutating func endDrag(translation dy: CGFloat, velocity vy: CGFloat) {
        if abs(vy) >= Self.flingVelocity {
            mode = vy < 0 ? .expanded : .collapsed
            return
        }
        guard travel > 0 else { return }
        switch mode {
        case .collapsed:
            // −dy ≥ 0.4 × travel, written without 0.4 so 160 of 400 is exactly 40 %.
            if -dy * 5 >= travel * 2 { mode = .expanded }
        case .expanded:
            // dy ≥ 0.2 × travel.
            if dy * 5 >= travel { mode = .collapsed }
        }
    }

    /// The playlist button or a tap on the peek header (Android `onPlayListClick`).
    mutating func expand() {
        mode = .expanded
    }

    mutating func collapse() {
        mode = .collapsed
    }

    /// A tap on the header.
    mutating func toggle() {
        mode = isExpanded ? .collapsed : .expanded
    }

    /// A row was chosen (Android `onSelected` → `Hidden`).
    mutating func didSelectItem() {
        mode = .collapsed
    }

    /// Next/Previous keep the mode; an empty queue has nothing to show.
    mutating func queueDidChange(count: Int) {
        if count == 0 { mode = .collapsed }
    }
}

/// Which queue rows the peek shows: the playing item, then the next one when there is room.
enum UpNextPeek {
    /// The peek never shows more than the current and the next item.
    static let rowLimit = 2

    /// Indices of the peek rows; `[]` (no panel) for an empty queue or an out-of-range index.
    static func rows(queueCount: Int, currentIndex: Int, maxRows: Int) -> [Int] {
        guard queueCount > 0, currentIndex >= 0, currentIndex < queueCount, maxRows > 0 else { return [] }
        let end = min(currentIndex + min(maxRows, rowLimit), queueCount)
        return Array(currentIndex..<end)
    }

    /// 2 rows normally; 1 at an accessibility text size or in a compact height (landscape iPhone),
    /// so the controls and the peek always fit.
    static func maxRows(isAccessibilitySize: Bool, isCompactHeight: Bool) -> Int {
        isAccessibilitySize || isCompactHeight ? 1 : rowLimit
    }
}
