import SwiftUI
import os

/// "Tiếp theo" at the bottom of the Player (Android `SlideArea`, without its ad; M5 §3.1), as a
/// system sheet that `PlayerView` keeps presented over itself, with two detents:
/// - peek: this header with the playing item (and the next one when it fits), filling the space
///   `PlayerView` keeps under the controls; the Player stays usable behind it;
/// - open: top just below the close row, the whole queue.
///
/// The system drives the drag: the sheet follows the finger, settles or flings to a detent, and
/// once the open list is scrolled to its top a further pull down drags the sheet itself down
/// (Apple Music's queue). A tap on the header, a row or VoiceOver escape switch it too.
///
/// The list stays mounted under the title and cross-fades with the peek rows as the sheet top
/// goes from the peek to the open position (`UpNextPanelState.progress`), so nothing jumps while
/// dragging. `upNext.root` / `upNext.item.<i>` exist only while open, as with the M3 sheet.
struct UpNextSheet: View {
    let engine: AudioEngine
    @Binding var panel: UpNextPanelState
    /// Indices of the peek rows (`UpNextPeek.rows`); never empty, `PlayerView` hides the sheet then.
    let peekRows: [Int]
    /// Screen y where the sheet top sits at the peek (the space under the controls) and open
    /// (just below the close row), from `PlayerView`.
    let peekTop: CGFloat
    let openTop: CGFloat
    /// Bottom safe area, so the last row clears the home indicator.
    let bottomInset: CGFloat
    /// Height of the collapsed header: the peek detent and the space under the controls.
    let onPeekHeightChange: (CGFloat) -> Void
    /// Screen y of the sheet top once it has stopped moving, so `PlayerView` can correct the
    /// detent heights to land exactly on its targets.
    let onSettledTop: (CGFloat) -> Void

    /// Current screen y of the sheet top, followed while it is dragged.
    @State private var top: CGFloat = 0
    /// Top of the list: just under the title.
    @State private var listTop: CGFloat = 0
    @State private var motion = UpNextPanelSignpost.Motion()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let coordinateSpace = "upNextSheet"

    var body: some View {
        let progress = panel.progress(top: top, peekTop: peekTop, openTop: openTop)
        // The header is drawn over the list: collapsed, its peek rows cover the (transparent,
        // untouchable) list; open, it is only the title bar above the list.
        ZStack(alignment: .top) {
            UpNextList(
                engine: engine,
                bottomInset: bottomInset,
                isShown: panel.isExpanded || progress > 0,
                isAccessible: panel.isExpanded
            ) { index in
                engine.select(index: index)
                withAnimation(animation) { panel.didSelectItem() }
            }
            .padding(.top, listTop)
            .opacity(UpNextPanelState.listOpacity(progress: progress))
            .allowsHitTesting(panel.isExpanded)
            UpNextHeader(
                engine: engine,
                peekRows: peekRows,
                isExpanded: panel.isExpanded,
                peekRowsOpacity: UpNextPanelState.peekRowsOpacity(progress: progress),
                // Clears the system drag indicator.
                topPadding: 22,
                titleCoordinateSpace: Self.coordinateSpace,
                onTitleBottom: { listTop = $0 + 12 }
            ) {
                motion.didMove()
                withAnimation(animation) { panel.toggle() }
            }
            .onGeometryChange(for: CGFloat.self, of: \.size.height) { height in
                // Only the collapsed header sizes the peek; the open one is shorter (no peek rows).
                if !panel.isExpanded { onPeekHeightChange(height) }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .coordinateSpace(.named(Self.coordinateSpace))
        .onGeometryChange(for: CGRect.self, of: { $0.frame(in: .global) }) { frame in
            top = frame.minY
            motion.top = frame.minY
            motion.onSettle = onSettledTop
            motion.didMove()
        }
        .accessibilityElement(children: .contain)
        .accessibilityAction(.escape) {
            withAnimation(animation) { panel.collapse() }
        }
    }

    /// Spring normally; a short ease without bounce with Reduce Motion.
    private var animation: Animation {
        reduceMotion ? .easeInOut(duration: 0.2) : .spring(duration: 0.35)
    }
}

/// "Tiếp theo" + the peek rows (collapsed only), one VoiceOver button: the top of the Up next
/// sheet, or the peek drawn in the Player itself in a compact height (landscape iPhone), where a
/// sheet cannot stay at a small height.
struct UpNextHeader: View {
    let engine: AudioEngine
    let peekRows: [Int]
    let isExpanded: Bool
    var peekRowsOpacity: CGFloat = 1
    var topPadding: CGFloat = 8
    /// Where `onTitleBottom` measures the title's bottom (the sheet's space), if anywhere.
    var titleCoordinateSpace: String?
    var onTitleBottom: (CGFloat) -> Void = { _ in }
    let action: () -> Void

    var body: some View {
        let state = engine.state
        Button(action: action) {
            VStack(alignment: .leading, spacing: 8) {
                Text("player.upNext")
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .onGeometryChange(for: CGFloat.self, of: { proxy in
                        titleCoordinateSpace.map { proxy.frame(in: .named($0)).maxY } ?? 0
                    }) { maxY in
                        onTitleBottom(maxY)
                    }
                if !isExpanded {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(peekRows, id: \.self) { index in
                            if state.queue.indices.contains(index) {
                                UpNextRow(item: state.queue[index], isCurrent: index == state.index, titleLineLimit: 1)
                            }
                        }
                    }
                    .opacity(peekRowsOpacity)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, topPadding)
            .padding(.bottom, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("player.upNext"))
        .accessibilityValue(Text(verbatim: String(format: String(localized: "player.upNext.peekValue"), state.current?.title ?? "")))
        .accessibilityHint(
            Text(isExpanded ? LocalizedStringKey("player.upNext.collapseHint") : LocalizedStringKey("player.upNext.expandHint"))
        )
        .accessibilityIdentifier("upNext.peek")
    }
}

/// Every move of the sheet (a drag, a settle, a tap or a fling) runs inside an os_signpost
/// animation interval (`tss.t.podcast` / `UpNextPanel` / `panel`), so Instruments and
/// `XCTOSSignpostMetric` report its hitches. The system animates the sheet, so the interval is
/// opened by the first height change and closed once the height has not changed for 0.25 s.
enum UpNextPanelSignpost {
    static let subsystem = AppLog.subsystem
    static let category = "UpNextPanel"
    static let name: StaticString = "panel"
    fileprivate static let signposter = OSSignposter(subsystem: subsystem, category: category)

    /// Also tells `onSettle` where the sheet top came to rest.
    @MainActor
    final class Motion {
        var top: CGFloat = 0
        var onSettle: ((CGFloat) -> Void)?
        private var interval: OSSignpostIntervalState?
        private var end: Task<Void, Never>?

        func didMove() {
            if interval == nil { interval = signposter.beginAnimationInterval(name) }
            end?.cancel()
            end = Task { @MainActor [weak self] in
                try? await Task.sleep(for: .milliseconds(250))
                guard !Task.isCancelled, let self else { return }
                if let interval = self.interval {
                    signposter.endInterval(name, interval)
                    self.interval = nil
                }
                self.onSettle?(self.top)
            }
        }
    }
}

/// The whole queue (the list of the M3 sheet): the playing row is marked and selected; a tap
/// plays that item in the same queue (`playerMediaItem`). The first row holds Previous / Next
/// (M6 §3.1: the Player's main row now has −15 / +30), disabled at the ends of the queue.
struct UpNextList: View {
    let engine: AudioEngine
    let bottomInset: CGFloat
    /// The panel is open or being dragged: each time it becomes true the list brings the playing
    /// row into view (while still transparent), so the open list never starts at row 0 with the
    /// playing item far below.
    let isShown: Bool
    /// In the accessibility tree (and so `upNext.root` / `upNext.item.<i>` exist) only while open.
    let isAccessible: Bool
    let select: (Int) -> Void

    var body: some View {
        let state = engine.state
        ScrollViewReader { proxy in
            list(state: state)
                .onChange(of: isShown) { _, shown in
                    if shown { proxy.scrollTo(engine.state.index, anchor: UnitPoint(x: 0.5, y: 0.25)) }
                }
        }
    }

    private func list(state: PlaybackState) -> some View {
        List {
            HStack(spacing: 12) {
                QueueStepButton(systemImage: "backward.end.fill", label: "player.previous", identifier: "player.previous") {
                    engine.skipPrevious()
                }
                .disabled(!state.hasPrevious)
                QueueStepButton(systemImage: "forward.end.fill", label: "player.next", identifier: "player.next") {
                    engine.skipNext()
                }
                .disabled(!state.hasNext)
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .accessibilityHidden(!isAccessible)
            ForEach(Array(state.queue.enumerated()), id: \.offset) { index, item in
                let isCurrent = index == state.index
                Button {
                    select(index)
                } label: {
                    UpNextRow(item: item, isCurrent: isCurrent)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .listRowBackground(Color.clear)
                .accessibilityAddTraits(isCurrent ? .isSelected : [])
                .accessibilityIdentifier(isAccessible ? "upNext.item.\(index)" : "")
                .accessibilityHidden(!isAccessible)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .contentMargins(.bottom, bottomInset, for: .scrollContent)
        // The list stays mounted while collapsed (no 30-row insert when opening). Its rows are
        // hidden from VoiceOver then and it has no identifier (XCUITest still sees hidden
        // elements).
        .accessibilityIdentifier(isAccessible ? "upNext.root" : "")
    }
}

/// Previous / Next episode at the top of the Up next list: glyph + text, ≥ 44 pt, half the row each.
private struct QueueStepButton: View {
    let systemImage: String
    let label: LocalizedStringKey
    let identifier: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(label, systemImage: systemImage)
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
                .frame(maxWidth: .infinity, minHeight: 44)
                .contentShape(Rectangle())
        }
        .glassButton()
        .accessibilityLabel(Text(label))
        .accessibilityIdentifier(identifier)
    }
}

/// One Up next row, shared by the peek and the list: artwork 48, "Đang phát" (with the coral dot
/// and waveform) on the playing item, bold title, podcast name.
struct UpNextRow: View {
    let item: PlaybackItem
    let isCurrent: Bool
    /// 1 in the peek (keeps it short), 2 in the list (as the M3 sheet).
    var titleLineLimit = 2

    var body: some View {
        HStack(spacing: 12) {
            ArtworkImage(url: item.artworkURL, referer: item.referer, cornerRadius: 8)
                .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 2) {
                if isCurrent {
                    HStack(spacing: 6) {
                        // Brand accent (Cam San Hô): decoration only, never text (BRAND §2).
                        Circle()
                            .fill(Color.brandAccent)
                            .frame(width: 6, height: 6)
                            .accessibilityHidden(true)
                        Label("player.nowPlaying", systemImage: "waveform")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.brandPrimary)
                    }
                }
                Text(verbatim: item.title)
                    .font(.subheadline.weight(isCurrent ? .bold : .regular))
                    .foregroundStyle(.primary)
                    .lineLimit(titleLineLimit)
                Text(verbatim: item.podcastTitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
    }
}
