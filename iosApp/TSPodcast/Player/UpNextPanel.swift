import SwiftUI
import os

/// "Tiếp theo" panel at the bottom of the Player (Android `SlideArea`, without its ad; M5 §3.1).
/// Collapsed, only its header shows: the peek with the playing item (and the next one when it
/// fits), sitting in the space `PlayerView` keeps under the controls. Expanded, its top sits just
/// below the close row and the whole queue is listed. Drag the header (40 % up opens, 20 % down
/// closes, a 1000 pt/s fling follows its direction), tap it, pick a row or VoiceOver escape.
///
/// `PlayerView` places it with its top at the expanded position; the panel moves itself down by
/// `UpNextPanelState.offset(forDrag:)`. The list stays mounted under the title bar at a fixed
/// place and cross-fades with the peek rows (`progress(forDrag:)`), so opening never inserts 30
/// rows mid-animation and nothing jumps when the header loses its peek rows. `upNext.root` /
/// `upNext.item.<i>` are in the accessibility tree only while the panel is open, as with the M3
/// sheet.
struct UpNextPanel: View {
    let engine: AudioEngine
    @Binding var panel: UpNextPanelState
    /// Indices of the peek rows (`UpNextPeek.rows`); never empty, `PlayerView` hides the panel then.
    let peekRows: [Int]
    /// Bottom safe area, so the last row clears the home indicator.
    let bottomInset: CGFloat
    /// Height of the collapsed header, for the space `PlayerView` keeps under the controls.
    let onPeekHeightChange: (CGFloat) -> Void

    @State private var dragY: CGFloat = 0
    @State private var isDragging = false
    /// True while a header drag is in progress; SwiftUI resets it even when the drag is cancelled
    /// (system gesture, interruption), which never calls `onEnded`.
    @GestureState private var isGestureActive = false
    /// Top of the list: just under the grabber and the title.
    @State private var listTop: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let cornerRadius: CGFloat = 28
    private static let coordinateSpace = "upNextPanel"

    var body: some View {
        let state = engine.state
        let progress = panel.progress(forDrag: dragY)
        // The header is drawn over the list: collapsed, its peek rows cover the (transparent,
        // untouchable) list; open, it is only the title bar above the list.
        ZStack(alignment: .top) {
            UpNextList(
                engine: engine,
                bottomInset: bottomInset,
                isShown: panel.isExpanded || isDragging,
                isAccessible: panel.isExpanded
            ) { index in
                engine.select(index: index)
                UpNextPanelSignpost.animate(animation) { panel.didSelectItem() }
            }
            .padding(.top, listTop)
            .modifier(PanelFade(progress: progress, opacity: UpNextPanelState.listOpacity))
            .allowsHitTesting(panel.isExpanded && !isDragging)
            header(state: state, progress: progress)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .coordinateSpace(.named(Self.coordinateSpace))
        .glassSurface(
            in: UnevenRoundedRectangle(
                topLeadingRadius: Self.cornerRadius,
                topTrailingRadius: Self.cornerRadius,
                style: .continuous
            )
        )
        .offset(y: panel.offset(forDrag: dragY))
        .accessibilityElement(children: .contain)
        .accessibilityAction(.escape) {
            UpNextPanelSignpost.animate(animation) { panel.collapse() }
        }
        .onChange(of: state.queue.count) { _, count in
            withAnimation(animation) { panel.queueDidChange(count: count) }
        }
        .onChange(of: isGestureActive) { _, active in
            // A cancelled drag never reaches `onEnded`: settle back to the current mode.
            guard !active, isDragging else { return }
            UpNextPanelSignpost.animate(animation) {
                dragY = 0
                isDragging = false
            }
        }
    }

    /// Spring normally; a short ease without bounce with Reduce Motion.
    private var animation: Animation {
        reduceMotion ? .easeInOut(duration: 0.2) : .spring(duration: 0.35)
    }

    /// Grabber + "Tiếp theo" + the peek rows (collapsed only, fading out as the panel opens), one
    /// VoiceOver button.
    private func header(state: PlaybackState, progress: CGFloat) -> some View {
        Button {
            UpNextPanelSignpost.animate(animation) { panel.toggle() }
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                VStack(alignment: .leading, spacing: 8) {
                    Capsule()
                        .fill(Color.secondary.opacity(0.5))
                        .frame(width: 36, height: 5)
                        .frame(maxWidth: .infinity)
                    Text("player.upNext")
                        .font(.headline)
                        .foregroundStyle(.primary)
                }
                .onGeometryChange(for: CGFloat.self, of: { $0.frame(in: .named(Self.coordinateSpace)).maxY }) { maxY in
                    // Same gap under the title as the header's bottom padding.
                    listTop = maxY + 12
                }
                if !panel.isExpanded {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(peekRows, id: \.self) { index in
                            if state.queue.indices.contains(index) {
                                UpNextRow(item: state.queue[index], isCurrent: index == state.index, titleLineLimit: 1)
                            }
                        }
                    }
                    .modifier(PanelFade(progress: progress, opacity: UpNextPanelState.peekRowsOpacity))
                    // Opening removes the rows: a removed view no longer follows the animated
                    // progress, so they fade out quickly on their own, before the list fades in.
                    .transition(.asymmetric(insertion: .identity, removal: .opacity.animation(.easeOut(duration: 0.1))))
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onGeometryChange(for: CGFloat.self, of: \.size.height) { height in
            // Only the collapsed header sizes the space under the controls; the expanded one is
            // shorter (no peek rows) and must not move the layout underneath.
            if !panel.isExpanded { onPeekHeightChange(height) }
        }
        .highPriorityGesture(
            // Global coordinates: the header moves with the drag, so in its own (local) space the
            // translation would shrink as the panel follows the finger and the panel would lag
            // and shake (seen on Tun: a 60 % drag moved it ~20 % and did not open it).
            DragGesture(minimumDistance: 8, coordinateSpace: .global)
                .updating($isGestureActive) { _, active, _ in active = true }
                .onChanged { value in
                    isDragging = true
                    dragY = value.translation.height
                }
                .onEnded { value in
                    UpNextPanelSignpost.animate(animation) {
                        panel.endDrag(translation: value.translation.height, velocity: value.velocity.height)
                        dragY = 0
                        isDragging = false
                    }
                }
        )
        .accessibilityLabel(Text("player.upNext"))
        .accessibilityValue(Text(verbatim: peekValue(state: state)))
        .accessibilityHint(
            Text(panel.isExpanded ? LocalizedStringKey("player.upNext.collapseHint") : LocalizedStringKey("player.upNext.expandHint"))
        )
        .accessibilityIdentifier("upNext.peek")
    }

    /// "Đang phát: <tiêu đề>".
    private func peekValue(state: PlaybackState) -> String {
        String(format: String(localized: "player.upNext.peekValue"), state.current?.title ?? "")
    }
}

/// Opacity as a function of how far open the panel is. Animatable, so during a tap's or a
/// release's spring the opacity follows the panel's animated position frame by frame (peek rows
/// out over the first half, list in over the second half) instead of interpolating straight
/// between the end values, which would show both at once.
private struct PanelFade: ViewModifier, Animatable {
    var progress: CGFloat
    let opacity: (CGFloat) -> CGFloat

    nonisolated var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        content.opacity(opacity(progress))
    }
}

/// Every animated move of the panel runs inside an os_signpost animation interval
/// (`tss.t.podcast` / `UpNextPanel` / `panel`), so Instruments and `XCTOSSignpostMetric` report
/// the hitches of the open/close animation.
enum UpNextPanelSignpost {
    static let subsystem = AppLog.subsystem
    static let category = "UpNextPanel"
    static let name: StaticString = "panel"
    private static let signposter = OSSignposter(subsystem: subsystem, category: category)

    @MainActor
    static func animate(_ animation: Animation, _ change: () -> Void) {
        let interval = signposter.beginAnimationInterval(name)
        withAnimation(animation, completionCriteria: .logicallyComplete, change) {
            signposter.endInterval(name, interval)
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
