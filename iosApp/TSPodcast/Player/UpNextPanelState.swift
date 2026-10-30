import CoreGraphics
import Foundation

/// Open/closed state of the Up next sheet (Android `SlideAreaState`). Pure value type so it is
/// unit tested; `PlayerView` keeps it in `@State` and maps it to the sheet's two detents. The
/// drag itself (finger tracking, thresholds, flings, the hand-off from a list scrolled to its top)
/// is the system sheet's (M5 §3.2).
struct UpNextPanelState: Equatable, Sendable {
    enum Mode: Equatable, Sendable {
        /// The peek under the player controls (Android `Hidden`: the "UP NEXT" header).
        case collapsed
        /// The full list, top just below the close row (Android `Expanded`).
        case expanded
    }

    var mode: Mode = .collapsed

    var isExpanded: Bool { mode == .expanded }

    /// How far open the sheet is with its top at screen y `top`: 0 at `peekTop` (the space under
    /// the controls), 1 at `openTop` (just below the close row), clamped. The peek rows fade out
    /// and the list fades in with it, so nothing jumps while the sheet is dragged or settles.
    /// Without measured targets (`openTop` not above `peekTop`), the mode.
    func progress(top: CGFloat, peekTop: CGFloat, openTop: CGFloat) -> CGFloat {
        guard peekTop > openTop else { return isExpanded ? 1 : 0 }
        return min(max((peekTop - top) / (peekTop - openTop), 0), 1)
    }

    /// The peek rows fade out over the first half of the opening and the list fades in over the
    /// second half, so the two never show on top of each other.
    static func peekRowsOpacity(progress: CGFloat) -> CGFloat {
        min(max(1 - progress * 2, 0), 1)
    }

    static func listOpacity(progress: CGFloat) -> CGFloat {
        min(max(progress * 2 - 1, 0), 1)
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
