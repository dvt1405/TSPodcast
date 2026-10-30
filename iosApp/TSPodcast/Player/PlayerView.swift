import SwiftUI
@preconcurrency import Shared

/// Full-screen Player (Android `PlayerScreen` + `PlayerArea` + `PlayerControl` + `PlayerProgress`):
/// blurred artwork background, close chevron, artwork, title, podcast name, seek slider with
/// time labels (or "LIVE"), then Favourite · −15 · Play/Pause · +30 · Playlist and, under it, the
/// speed and sleep timer buttons (M6 §3.1; Previous/Next moved to the Up next sheet). Under the
/// controls sits the "Tiếp theo" peek (Android `SlideArea` header, without its ad; M5 §3.1/§4):
/// the last item of the column is a space as tall as the measured peek, and `UpNextSheet` is a
/// system sheet whose peek detent is exactly that space, so the peek never covers the controls.
/// Open, the sheet top sits just below the close row. The sheet stays presented while the Player
/// is (the Player behind it stays usable), so the Player's own sheets and alerts are presented
/// from the sheet then (`PlayerPresentations`). The close row's `…` (`player.more`) only holds "Báo cáo nội
/// dung" and shows only when a report address is configured (M6 §3.5).
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
    /// Height of the column (inside the safe area): the open detent is the part under the close row.
    @State private var columnHeight: CGFloat = 0
    /// The Up next sheet is presented once the Player is on screen (not during the cover's
    /// presentation).
    @State private var hasAppeared = false
    /// Screen y of the close row's bottom and of the peek space's top: where the open and the
    /// collapsed sheet tops must land.
    @State private var openTarget: CGFloat = 0
    @State private var peekTarget: CGFloat = 0
    /// Added to the detent heights so the sheet tops land on the targets: a detent height is not
    /// exactly the distance from the screen bottom (iOS 26 floats a partial sheet above the bottom
    /// edge; Tun put the open top 23 pt low). Measured when the sheet settles, per column height.
    @State private var detentCorrection = UpNextDetentCorrection()
    @State private var bottomInset: CGFloat = 0
    @State private var isSpeedSheetPresented = false
    @State private var isSleepTimerSheetPresented = false
    /// Address shown when no app could open the report mail.
    @State private var reportFallbackEmail: String?

    @Environment(\.openURL) private var openURL

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
                    if environment.contentReporter.isAvailable, let item = state.current {
                        moreMenu(for: item)
                    }
                }
                .padding(.horizontal, 8)
                .onGeometryChange(for: CGFloat.self, of: { $0.frame(in: .named(playerCoordinateSpace)).maxY }) { bottom in
                    closeRowBottom = bottom
                }
                .onGeometryChange(for: CGFloat.self, of: { $0.frame(in: .global).maxY }) { bottom in
                    openTarget = bottom
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

                PlayerSecondaryControls(
                    state: state,
                    openSpeed: { isSpeedSheetPresented = true },
                    openSleepTimer: { isSleepTimerSheetPresented = true }
                )
                .padding(.horizontal, 16)
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
                    if isCompactHeight {
                        // Landscape iPhone: a sheet there always fills the height, so the peek is
                        // drawn here and the queue opens as a full sheet.
                        UpNextHeader(engine: engine, peekRows: peekRows, isExpanded: false) {
                            withAnimation(panelAnimation) { panel.expand() }
                        }
                    } else {
                        Color.clear
                            .frame(height: peekHeight)
                            .accessibilityHidden(true)
                            .onGeometryChange(for: CGFloat.self, of: { $0.frame(in: .global).minY }) { top in
                                peekTarget = top
                            }
                    }
                }
            }
            .coordinateSpace(.named(playerCoordinateSpace))
            .onGeometryChange(for: CGFloat.self, of: \.size.height) { height in
                columnHeight = height
                detentCorrection.layoutDidChange(columnHeight: height)
            }
            .onGeometryChange(for: CGFloat.self, of: \.safeAreaInsets.bottom) { inset in
                bottomInset = inset
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.root")
        .onAppear { hasAppeared = true }
        .sheet(isPresented: isUpNextPresented(hasQueue: !peekRows.isEmpty)) {
            let detents = upNextDetents
            UpNextSheet(
                engine: engine,
                panel: $panel,
                peekRows: peekRows,
                peekTop: peekTarget,
                openTop: openTarget,
                bottomInset: bottomInset,
                onPeekHeightChange: { height in
                    if !isCompactHeight { peekHeight = height }
                },
                onSettledTop: { top in
                    guard !isCompactHeight else { return }
                    detentCorrection.settled(
                        top: top,
                        target: panel.isExpanded ? openTarget : peekTarget,
                        isOpen: panel.isExpanded
                    )
                }
            )
            .presentationDetents(
                [detents.peek, detents.open],
                selection: Binding(
                    get: { panel.isExpanded ? detents.open : detents.peek },
                    set: { panel.mode = $0 == detents.open ? .expanded : .collapsed }
                )
            )
            .presentationDragIndicator(.visible)
            // The Player (close, controls, speed, timer) stays usable at both detents, undimmed.
            .presentationBackgroundInteraction(.enabled)
            // Never dismissed by a drag, except the full sheet of a compact height (it collapses).
            .interactiveDismissDisabled(!isCompactHeight)
            .modifier(presentations(isActive: true))
        }
        .modifier(presentations(isActive: !isUpNextPresented(hasQueue: !peekRows.isEmpty).wrappedValue))
        .onChange(of: state.isLive) { _, isLive in
            // Live has no speed (M6 §3.1): never leave the sheet open on a live stream.
            if isLive { isSpeedSheetPresented = false }
        }
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

    /// `…` of the close row (M6 §3.5): "Báo cáo nội dung" for the current episode. Shown only
    /// when a report address is configured, since it is the menu's only entry.
    private func moreMenu(for item: PlaybackItem) -> some View {
        Menu {
            Button {
                report(item)
            } label: {
                Label("report.action", systemImage: "exclamationmark.bubble")
            }
            .accessibilityIdentifier("player.report")
        } label: {
            Image(systemName: "ellipsis")
                .font(.title3.weight(.semibold))
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .glassCircleButton()
        .accessibilityLabel(Text("player.more"))
        .accessibilityIdentifier("player.more")
    }

    private func report(_ item: PlaybackItem) {
        let openURL = openURL
        environment.contentReporter.report(
            kind: .episode,
            feedId: item.feedId,
            episodeId: Int64(item.id),
            screen: AudioEngine.analyticsScreen,
            open: { url, completion in openURL(url, completion: completion) },
            failed: { email in reportFallbackEmail = email }
        )
    }

    /// Spring normally; a short ease without bounce with Reduce Motion (same as `UpNextSheet`).
    private var panelAnimation: Animation {
        reduceMotion ? .easeInOut(duration: 0.2) : .spring(duration: 0.35)
    }

    /// The Up next sheet's detents: the measured peek (its top on the space under the controls)
    /// and open (its top just below the close row). A sheet height excludes the bottom safe area,
    /// as the column does. Until measured, a plausible peek and the open one kept above it.
    private var upNextDetents: (peek: PresentationDetent, open: PresentationDetent) {
        let peekHeight = max((peekHeight > 0 ? peekHeight : 160) + detentCorrection.peek, 60)
        let openHeight = max(columnHeight - closeRowBottom + detentCorrection.open, peekHeight + 1)
        return (.height(peekHeight), .height(openHeight))
    }

    private var isCompactHeight: Bool { verticalSizeClass == .compact }

    /// Presented while the Player is on screen with a queue; in a compact height only while open
    /// (the peek is in the Player then), and dismissing it there collapses.
    private func isUpNextPresented(hasQueue: Bool) -> Binding<Bool> {
        Binding(
            get: { hasAppeared && hasQueue && (!isCompactHeight || panel.isExpanded) },
            set: { presented in
                if !presented { panel.collapse() }
            }
        )
    }

    /// The Player's sheets and alerts, attached to whichever view presents: the Up next sheet
    /// while it is up (a view that already presents a sheet cannot present another), else the
    /// Player itself.
    private func presentations(isActive: Bool) -> PlayerPresentations {
        PlayerPresentations(
            engine: engine,
            notifications: environment.notifications,
            isActive: isActive,
            isSpeedSheetPresented: $isSpeedSheetPresented,
            isSleepTimerSheetPresented: $isSleepTimerSheetPresented,
            reportFallbackEmail: $reportFallbackEmail
        )
    }
}

/// Speed and sleep timer sheets, the report fallback alert and the push prompts of the Player.
/// Inactive, it presents nothing (its bindings read false), so only one view presents them.
private struct PlayerPresentations: ViewModifier {
    let engine: AudioEngine
    let notifications: NotificationsModel
    let isActive: Bool
    @Binding var isSpeedSheetPresented: Bool
    @Binding var isSleepTimerSheetPresented: Bool
    @Binding var reportFallbackEmail: String?

    func body(content: Content) -> some View {
        content
            .sheet(isPresented: isActive ? $isSpeedSheetPresented : .constant(false)) {
                SpeedSheet(engine: engine)
            }
            .sheet(isPresented: isActive ? $isSleepTimerSheetPresented : .constant(false)) {
                SleepTimerSheet(engine: engine)
            }
            .contentReportFallbackAlert(email: isActive ? $reportFallbackEmail : .constant(nil))
            // The first favourite is set in the Player, so the one-time push prompt shows here
            // (M4 §4.4).
            .notificationsPrompt(notifications, isActive: isActive)
            .notificationsDeniedAlert(notifications, isActive: isActive)
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
/// e.g. VoiceOver or XCUITest `adjust`). Reads `engine.position`, so only this view follows the
/// 4 Hz ticks.
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
            let position = engine.position
            let fraction = scrubbing ?? position.progress
            let shownMs = scrubbing.map { Int64($0 * Double(position.durationMs)) } ?? position.positionMs
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
                            scrubbing = position.progress
                        } else {
                            if let scrubbing { engine.seek(toFraction: scrubbing) }
                            scrubbing = nil
                        }
                    }
                )
                .tint(Color.brandSecondary)
                .disabled(position.durationMs <= 0)
                .accessibilityIdentifier("player.slider")
                HStack {
                    Text(verbatim: PlaybackProgressKt.formatPlaybackTime(ms: shownMs))
                        .accessibilityIdentifier("player.elapsed")
                    Spacer()
                    Text(verbatim: PlaybackProgressKt.formatPlaybackTime(ms: position.durationMs))
                        .accessibilityIdentifier("player.duration")
                }
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
            }
        }
    }
}

/// Favourite · −15 · Play/Pause · +30 · Playlist (`PlayerControl`, M6 §3.1: skip replaces
/// Previous/Next, which live in the Up next panel). On iOS 26 the four secondary buttons are glass
/// circles and Play/Pause is tinted glass, all in one `GlassGroup` so they share one glass layer;
/// below, the M3 row (plain glyphs, a `brandSecondary` circle). Skip is disabled for live.
private struct PlayerControlsView: View {
    let engine: AudioEngine
    let state: PlaybackState
    let openPlaylist: () -> Void

    var body: some View {
        let showsPause = state.isPlaying || state.isBuffering
        let canSkip = state.current != nil && !state.isLive
        GlassGroup(spacing: 8) {
            HStack {
                ControlButton(systemImage: state.isFavourite ? "heart.fill" : "heart", label: "player.favourite", identifier: "player.favourite") {
                    engine.toggleFavourite()
                }
                .accessibilityAddTraits(state.isFavourite ? .isSelected : [])
                Spacer(minLength: 0)
                ControlButton(systemImage: "gobackward.15", label: "player.skipBack", identifier: "player.skipBack") {
                    engine.skip(bySeconds: -RemoteCommands.skipBackwardSeconds)
                }
                .disabled(!canSkip)
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
                                .font(.largeTitle.weight(.bold))
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
                ControlButton(systemImage: "goforward.30", label: "player.skipForward", identifier: "player.skipForward") {
                    engine.skip(bySeconds: RemoteCommands.skipForwardSeconds)
                }
                .disabled(!canSkip)
                Spacer(minLength: 0)
                ControlButton(systemImage: "list.bullet", label: "player.playlist", identifier: "player.playlist", action: openPlaylist)
            }
        }
    }
}

/// Speed ("1.0×") and sleep timer (moon, then the time left or "Hết tập") under the main row
/// (M6 §3.1). Speed is disabled for live; the timer works for anything that plays.
private struct PlayerSecondaryControls: View {
    let state: PlaybackState
    let openSpeed: () -> Void
    let openSleepTimer: () -> Void

    @Environment(\.locale) private var locale

    var body: some View {
        HStack(spacing: 12) {
            Button(action: openSpeed) {
                Text(verbatim: SpeedSteps.label(state.speedTenths))
                    .font(.subheadline.weight(.semibold).monospacedDigit())
                    .frame(minWidth: 64, minHeight: 44)
                    .contentShape(Capsule())
            }
            .glassButton()
            .disabled(state.current == nil || state.isLive)
            .accessibilityLabel(Text("player.speed"))
            .accessibilityValue(Text(verbatim: SpeedSteps.spokenLabel(state.speedTenths, locale: locale)))
            .accessibilityIdentifier("player.speed")

            // Ticks once a second only while a minutes timer runs.
            TimelineView(.periodic(from: .now, by: state.sleepTimer.deadline == nil ? 3600 : 1)) { context in
                Button(action: openSleepTimer) {
                    HStack(spacing: 6) {
                        Image(systemName: "moon.zzz")
                        if let text = sleepTimerText(at: context.date) {
                            Text(verbatim: text)
                                .font(.subheadline.weight(.semibold).monospacedDigit())
                        }
                    }
                    .frame(minWidth: 64, minHeight: 44)
                    .contentShape(Capsule())
                }
                .glassButton()
                .accessibilityLabel(Text("player.sleepTimer"))
                .accessibilityValue(Text(verbatim: sleepTimerValue(at: context.date)))
                .accessibilityIdentifier("player.sleepTimer")
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// "28:14" or "Hết tập"; nothing when off (the moon alone).
    private func sleepTimerText(at now: Date) -> String? {
        switch state.sleepTimer.setting {
        case .off:
            nil
        case .minutes:
            SleepTimerStatus.clockText(remainingMs: state.sleepTimer.remainingMs(at: now) ?? 0)
        case .endOfEpisode:
            String(localized: "sleepTimer.option.endOfEpisode")
        }
    }

    /// VoiceOver: "Còn 28:14", "Hết tập" or "Tắt".
    private func sleepTimerValue(at now: Date) -> String {
        switch state.sleepTimer.setting {
        case .off:
            String(localized: "player.sleepTimer.off")
        case .minutes:
            String(
                format: String(localized: "player.sleepTimer.remaining"),
                SleepTimerStatus.clockText(remainingMs: state.sleepTimer.remainingMs(at: now) ?? 0)
            )
        case .endOfEpisode:
            String(localized: "sleepTimer.option.endOfEpisode")
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

/// Corrections of the Up next detent heights (see `PlayerView.detentCorrection`): when the sheet
/// settles at a detent, the distance between its top and the target is added to that detent's
/// height, once per column height (rotation, Dynamic Type), and only for a small miss: a sheet
/// held still mid-drag is far from both targets and is ignored.
struct UpNextDetentCorrection: Equatable {
    /// A larger miss is a sheet held mid-drag, not a detent offset.
    static let maxMiss: CGFloat = 40

    private(set) var peek: CGFloat = 0
    private(set) var open: CGFloat = 0
    private var peekDone = false
    private var openDone = false
    private var columnHeight: CGFloat = 0

    /// `top` / `target`: screen y of the settled sheet top and of where it must be.
    mutating func settled(top: CGFloat, target: CGFloat, isOpen: Bool) {
        let miss = top - target
        guard target > 0, abs(miss) > 0.5, abs(miss) <= Self.maxMiss else {
            if isOpen { openDone = openDone || abs(miss) <= 0.5 } else { peekDone = peekDone || abs(miss) <= 0.5 }
            return
        }
        if isOpen, !openDone {
            open += miss
            openDone = true
        } else if !isOpen, !peekDone {
            peek += miss
            peekDone = true
        }
    }

    /// A new layout: measure again (the offsets are kept as the starting point).
    mutating func layoutDidChange(columnHeight: CGFloat) {
        guard abs(columnHeight - self.columnHeight) > 0.5 else { return }
        self.columnHeight = columnHeight
        peekDone = false
        openDone = false
    }
}
