import SwiftUI

/// "Tiếp theo" panel at the bottom of the Player (Android `SlideArea`, without its ad; M5 §3.1).
/// Collapsed, only its header shows: the peek with the playing item (and the next one when it
/// fits), sitting in the space `PlayerView` keeps under the controls. Expanded, its top sits just
/// below the close row and the whole queue is listed. Drag the header (40 % up opens, 20 % down
/// closes, a 1000 pt/s fling follows its direction), tap it, pick a row or VoiceOver escape.
///
/// `PlayerView` places it with its top at the expanded position; the panel moves itself down by
/// `UpNextPanelState.offset(forDrag:)`. `upNext.root` / `upNext.item.<i>` exist only while the
/// list is shown, as with the M3 sheet.
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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let cornerRadius: CGFloat = 28

    var body: some View {
        let state = engine.state
        VStack(spacing: 0) {
            header(state: state)
            if panel.isExpanded || isDragging {
                UpNextList(engine: engine, bottomInset: bottomInset) { index in
                    engine.select(index: index)
                    withAnimation(animation) { panel.didSelectItem() }
                }
                .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
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
            withAnimation(animation) { panel.collapse() }
        }
        .onChange(of: state.queue.count) { _, count in
            withAnimation(animation) { panel.queueDidChange(count: count) }
        }
    }

    /// Spring normally; a short ease without bounce with Reduce Motion.
    private var animation: Animation {
        reduceMotion ? .easeInOut(duration: 0.2) : .spring(duration: 0.35)
    }

    /// Grabber + "Tiếp theo" + the peek rows (collapsed only), one VoiceOver button.
    private func header(state: PlaybackState) -> some View {
        Button {
            withAnimation(animation) { panel.toggle() }
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                Capsule()
                    .fill(Color.secondary.opacity(0.5))
                    .frame(width: 36, height: 5)
                    .frame(maxWidth: .infinity)
                Text("player.upNext")
                    .font(.headline)
                    .foregroundStyle(.primary)
                if !panel.isExpanded {
                    ForEach(peekRows, id: \.self) { index in
                        if state.queue.indices.contains(index) {
                            UpNextRow(item: state.queue[index], isCurrent: index == state.index, titleLineLimit: 1)
                        }
                    }
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
            DragGesture(minimumDistance: 8)
                .onChanged { value in
                    isDragging = true
                    dragY = value.translation.height
                }
                .onEnded { value in
                    withAnimation(animation) {
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

/// The whole queue (the list of the M3 sheet): the playing row is marked and selected; a tap
/// plays that item in the same queue (`playerMediaItem`). The first row holds Previous / Next
/// (M6 §3.1: the Player's main row now has −15 / +30), disabled at the ends of the queue.
struct UpNextList: View {
    let engine: AudioEngine
    let bottomInset: CGFloat
    let select: (Int) -> Void

    var body: some View {
        let state = engine.state
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
                .accessibilityIdentifier("upNext.item.\(index)")
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .contentMargins(.bottom, bottomInset, for: .scrollContent)
        .accessibilityIdentifier("upNext.root")
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
