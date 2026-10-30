import SwiftUI
@preconcurrency import Shared

/// Full-screen Player (Android `PlayerScreen` + `PlayerArea` + `PlayerControl` + `PlayerProgress`):
/// blurred artwork background, close chevron, artwork, title, podcast name, seek slider with
/// time labels (or "LIVE"), then Favourite · Previous · Play/Pause · Next · Playlist. Under the
/// controls sits the "Tiếp theo" peek (Android `SlideArea` header, without its ad; M5 §3.1/§4):
/// the last item of the column is a space as tall as the measured peek, and `UpNextPanel` is
/// drawn over that space, so the collapsed peek never covers the controls. Expanded, the panel
/// top sits just below the close row. No "More" button (M3 §7 item 2).
/// Coordinate space of the Player column, where the close row and the peek space are measured.
private let playerCoordinateSpace = "player"

struct PlayerView: View {
    let engine: AudioEngine

    @Environment(AppEnvironment.self) private var environment
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// New on every `fullScreenCover`, so reopening the Player always starts at the peek.
    @State private var panel = UpNextPanelState()
    @State private var peekHeight: CGFloat = 0
    @State private var closeRowBottom: CGFloat = 0
    @State private var peekTop: CGFloat = 0
    @State private var bottomInset: CGFloat = 0

    var body: some View {
        let state = engine.state
        let peekRows = UpNextPeek.rows(
            queueCount: state.queue.count,
            currentIndex: state.index,
            maxRows: UpNextPeek.maxRows(
                isAccessibilitySize: dynamicTypeSize.isAccessibilitySize,
                isCompactHeight: verticalSizeClass == .compact
            )
        )
        ZStack {
            PlayerBackground(item: state.current)
            VStack(spacing: 0) {
                HStack {
                    Button {
                        environment.isPlayerPresented = false
                    } label: {
                        Image(systemName: "chevron.down")
                            .font(.title2.weight(.semibold))
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    // Glass circle on iOS 26, the plain M3 chevron below (M5 §3.3).
                    .glassCircleButton()
                    .accessibilityLabel(Text("player.close"))
                    .accessibilityIdentifier("player.close")
                    Spacer()
                }
                .padding(.horizontal, 8)
                .onGeometryChange(for: CGFloat.self, of: { $0.frame(in: .named(playerCoordinateSpace)).maxY }) { bottom in
                    closeRowBottom = bottom
                    updateTravel()
                }

                Spacer(minLength: 8)

                // The flexible part: it shrinks first so controls + peek always fit (§3.5).
                ArtworkImage(
                    url: state.current?.artworkURL,
                    referer: state.current?.referer,
                    cornerRadius: 12,
                    accessibilityLabel: state.current?.title
                )
                .aspectRatio(1, contentMode: .fit)
                .padding(.horizontal, 30)
                .accessibilityIdentifier("player.artwork")

                VStack(alignment: .leading, spacing: 4) {
                    Text(verbatim: state.current?.title ?? "")
                        .font(.title3.weight(.bold))
                        .lineLimit(2, reservesSpace: true)
                        .accessibilityIdentifier("player.title")
                    Text(verbatim: state.current?.podcastTitle ?? "")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .accessibilityIdentifier("player.subtitle")
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.top, 20)
                .layoutPriority(1)

                PlayerProgressView(engine: engine, state: state)
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .layoutPriority(1)

                PlayerControlsView(engine: engine, state: state) {
                    withAnimation(panelAnimation) { panel.expand() }
                }
                .padding(.horizontal, 8)
                .padding(.top, 8)
                .layoutPriority(1)

                if state.error != nil {
                    PlayerErrorView { engine.retry() }
                        .padding(.top, 8)
                        .layoutPriority(1)
                }

                Spacer(minLength: 16)

                // The space the collapsed peek occupies: always the last item, under the controls
                // (and under the error view), so the panel can never overlay the transport row.
                if !peekRows.isEmpty {
                    Color.clear
                        .frame(height: peekHeight)
                        .accessibilityHidden(true)
                        .onGeometryChange(for: CGFloat.self, of: { $0.frame(in: .named(playerCoordinateSpace)).minY }) { top in
                            peekTop = top
                            updateTravel()
                        }
                }
            }
            .coordinateSpace(.named(playerCoordinateSpace))
            .onGeometryChange(for: CGFloat.self, of: \.safeAreaInsets.bottom) { inset in
                bottomInset = inset
            }
            .overlay(alignment: .top) {
                if !peekRows.isEmpty {
                    UpNextPanel(
                        engine: engine,
                        panel: $panel,
                        peekRows: peekRows,
                        bottomInset: bottomInset
                    ) { height in
                        peekHeight = height
                    }
                    .padding(.top, closeRowBottom)
                    .ignoresSafeArea(.container, edges: .bottom)
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.root")
        // The first favourite is set here, so the one-time push prompt shows here (M4 §4.4).
        .notificationsPrompt(environment.notifications)
        .notificationsDeniedAlert(environment.notifications)
        #if DEBUG
        .overlay(alignment: .topTrailing) {
            if environment.launchOptions.playbackProbe {
                PlaybackProbe(engine: engine)
                    .padding(.top, 2)
                    .padding(.trailing, 8)
            }
        }
        #endif
    }

    /// Spring normally; a short ease without bounce with Reduce Motion (same as `UpNextPanel`).
    private var panelAnimation: Animation {
        reduceMotion ? .easeInOut(duration: 0.2) : .spring(duration: 0.35)
    }

    /// Distance between the panel top at the peek and just below the close row.
    private func updateTravel() {
        panel.travel = max(peekTop - closeRowBottom, 0)
    }
}

/// Artwork blurred over the system background (Android: blur 50 dp, alpha 0.6).
private struct PlayerBackground: View {
    let item: PlaybackItem?

    var body: some View {
        ZStack {
            Color(.systemBackground)
            ArtworkImage(url: item?.artworkURL, referer: item?.referer, cornerRadius: 0)
                .blur(radius: 50, opaque: true)
                .opacity(0.6)
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}

/// Slider + elapsed/total (`PlayerProgress`), or the "LIVE" badge for a stream without duration.
/// The slider seeks when the drag ends (or right away when the value is set without a drag,
/// e.g. VoiceOver or XCUITest `adjust`).
private struct PlayerProgressView: View {
    let engine: AudioEngine
    let state: PlaybackState

    @State private var scrubbing: Double?

    var body: some View {
        if state.isLive {
            Text("player.live")
                .font(.caption.weight(.bold))
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(Capsule().fill(Color.red))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier("player.live")
        } else {
            let fraction = scrubbing ?? state.progress
            let shownMs = scrubbing.map { Int64($0 * Double(state.durationMs)) } ?? state.positionMs
            VStack(spacing: 4) {
                Slider(
                    value: Binding(
                        get: { fraction },
                        set: { value in
                            if scrubbing != nil {
                                scrubbing = value
                            } else {
                                engine.seek(toFraction: value)
                            }
                        }
                    ),
                    in: 0...1,
                    onEditingChanged: { editing in
                        if editing {
                            scrubbing = state.progress
                        } else {
                            if let scrubbing { engine.seek(toFraction: scrubbing) }
                            scrubbing = nil
                        }
                    }
                )
                .tint(Color.brandSecondary)
                .disabled(state.durationMs <= 0)
                .accessibilityIdentifier("player.slider")
                HStack {
                    Text(verbatim: PlaybackProgressKt.formatPlaybackTime(ms: shownMs))
                        .accessibilityIdentifier("player.elapsed")
                    Spacer()
                    Text(verbatim: PlaybackProgressKt.formatPlaybackTime(ms: state.durationMs))
                        .accessibilityIdentifier("player.duration")
                }
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
            }
        }
    }
}

/// Favourite · Previous · Play/Pause · Next · Playlist (`PlayerControl`). On iOS 26 the four
/// secondary buttons are glass circles and Play/Pause is tinted glass, all in one `GlassGroup` so
/// they share one glass layer; below, the M3 row (plain glyphs, a `brandSecondary` circle).
private struct PlayerControlsView: View {
    let engine: AudioEngine
    let state: PlaybackState
    let openPlaylist: () -> Void

    var body: some View {
        let showsPause = state.isPlaying || state.isBuffering
        GlassGroup(spacing: 8) {
            HStack {
                ControlButton(systemImage: state.isFavourite ? "heart.fill" : "heart", label: "player.favourite", identifier: "player.favourite") {
                    engine.toggleFavourite()
                }
                .accessibilityAddTraits(state.isFavourite ? .isSelected : [])
                Spacer(minLength: 0)
                ControlButton(systemImage: "backward.end.fill", label: "player.previous", identifier: "player.previous") {
                    engine.skipPrevious()
                }
                .disabled(!state.hasPrevious)
                Spacer(minLength: 0)
                Button {
                    engine.togglePlayPause()
                } label: {
                    ZStack {
                        if state.isBuffering {
                            ProgressView()
                                .tint(.white)
                                .controlSize(.large)
                        } else {
                            Image(systemName: showsPause ? "pause.fill" : "play.fill")
                                .font(.system(size: 34, weight: .bold))
                                .foregroundStyle(.white)
                        }
                    }
                    .frame(width: 84, height: 84)
                    .contentShape(Circle())
                }
                .glassProminentButton(tint: .brandPrimary, in: Circle(), fallbackFill: .brandSecondary)
                .accessibilityLabel(Text(showsPause ? "player.pause" : "player.play"))
                .accessibilityIdentifier("player.playPause")
                .overlay {
                    if state.isBuffering {
                        Color.clear
                            .accessibilityElement()
                            .accessibilityLabel(Text("player.loading"))
                            .accessibilityIdentifier("player.buffering")
                            .allowsHitTesting(false)
                    }
                }
                Spacer(minLength: 0)
                ControlButton(systemImage: "forward.end.fill", label: "player.next", identifier: "player.next") {
                    engine.skipNext()
                }
                .disabled(!state.hasNext)
                Spacer(minLength: 0)
                ControlButton(systemImage: "list.bullet", label: "player.playlist", identifier: "player.playlist", action: openPlaylist)
            }
        }
    }
}

/// A 56 pt round button (hit target ≥ 44, M5 §3.5): a glass circle on iOS 26, the plain M3
/// glyph below.
private struct ControlButton: View {
    let systemImage: String
    let label: LocalizedStringKey
    let identifier: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.title2)
                .frame(width: 56, height: 56)
                .contentShape(Rectangle())
        }
        .glassCircleButton()
        .accessibilityLabel(Text(label))
        .accessibilityIdentifier(identifier)
    }
}

/// "Không phát được tập này" + "Tải lại" ([Sửa]: Android shows nothing when ExoPlayer fails).
private struct PlayerErrorView: View {
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            Text("player.error")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("player.error")
            // Glass prominent on iOS 26, `.borderedProminent` below; `.large` keeps it ≥ 44 pt tall.
            Button("action.retry", action: retry)
                .glassProminentButton(tint: .brandPrimary)
                .controlSize(.large)
                .accessibilityIdentifier("player.retry")
        }
    }
}
