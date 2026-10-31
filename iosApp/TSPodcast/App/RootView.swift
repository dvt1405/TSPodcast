import SwiftUI

/// Switches between Onboarding, category selection and Main. The start route is read once per
/// launch; until it is known (a few ms) the launch screen stays on (brand blue and the mark, the
/// same frame as `UILaunchScreen`), without a spinner. On a first launch a short brand intro
/// plays before Onboarding. The privacy sheet shows over the first screen when no consent is
/// saved yet.
struct RootView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var route: AppRoute?
    @State private var introFinished = false

    var body: some View {
        let consent = environment.consent
        content
            .animation(.default, value: route)
            .animation(.easeInOut(duration: 0.45), value: introFinished)
            .task {
                guard route == nil else { return }
                route = await environment.resolveStartRoute()
            }
            // First launch (or a new consent text): over whatever screen comes first, never
            // instead of it (M6 §3.6). Not over the intro: it waits for Onboarding.
            .sheet(isPresented: Binding(
                get: { route != nil && !isIntroShowing && consent.isPromptPresented },
                set: { consent.isPromptPresented = $0 }
            )) {
                ConsentSheet(
                    consent: environment.consent,
                    privacyURL: SettingsModel.privacyURL(from: Bundle.main.infoDictionary ?? [:])
                )
            }
    }

    /// The intro plays once per launch that starts on Onboarding (a first launch), never under
    /// UI tests (`LaunchOptions.skipIntro`).
    private var isIntroShowing: Bool {
        route == .onboarding && !introFinished && !environment.launchOptions.skipIntro
    }

    @ViewBuilder
    private var content: some View {
        switch route {
        case nil:
            LaunchFrame()
        case .onboarding:
            if isIntroShowing {
                BrandIntroView { introFinished = true }
                    .transition(.opacity)
            } else {
                OnboardingView(model: environment.makeOnboardingModel()) { route = .selectCategories }
                    .transition(.opacity)
            }
        case .selectCategories:
            CategorySelectionView(model: environment.makeCategorySelectionModel()) { route = .main }
        case .main:
            MainTabView(
                favouriteModel: environment.makeFavouriteModel(),
                homeModel: environment.makeHomeModel(),
                searchModel: environment.makeSearchModel()
            )
        }
    }
}

// MARK: - Brand mark

/// The "Lên sóng" mark on its 100-unit grid (`MARK` in `brand/scripts/lib.js`), placed like the
/// launch image (`brand/scripts/ios.js`): centred in a square of `side` points, the inked box
/// (x 7…96.5, y 16.5…91) filling 95 % of it.
struct BrandMarkLayout {
    let side: CGFloat

    var scale: CGFloat { side * 0.95 / 89.5 }
    var lineWidth: CGFloat { 14 * scale }
    var dotCenter: CGPoint { point(89, 24) }
    var dotDiameter: CGFloat { 15 * scale }

    /// A grid point in the square's coordinates.
    func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
        CGPoint(x: side / 2 + (x - 51.75) * scale, y: side / 2 + (y - 53.75) * scale)
    }
}

/// T + S as one stroke: the T crossbar and stem, then the S as two half circles (radius 14 over
/// radius 16, a sound wave) ending in a short tail. Arcs are polylines, smooth at this weight.
struct BrandMarkStroke: Shape {
    func path(in rect: CGRect) -> Path {
        let layout = BrandMarkLayout(side: min(rect.width, rect.height))
        let dx = rect.minX + (rect.width - layout.side) / 2
        let dy = rect.minY + (rect.height - layout.side) / 2
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            let point = layout.point(x, y)
            return CGPoint(x: point.x + dx, y: point.y + dy)
        }
        var path = Path()
        // T
        path.move(to: p(14, 24))
        path.addLine(to: p(70, 24))
        path.move(to: p(31, 24))
        path.addLine(to: p(31, 84))
        // S: (66,24) → left bowl → (66,52) → right bowl → (66,84) → tail to (54,84)
        path.move(to: p(66, 24))
        let steps = 32
        for i in 1...steps {
            let angle = (-90 - 180 * CGFloat(i) / CGFloat(steps)) * .pi / 180
            path.addLine(to: p(66 + 14 * cos(angle), 38 + 14 * sin(angle)))
        }
        for i in 1...steps {
            let angle = (-90 + 180 * CGFloat(i) / CGFloat(steps)) * .pi / 180
            path.addLine(to: p(66 + 16 * cos(angle), 68 + 16 * sin(angle)))
        }
        path.addLine(to: p(54, 84))
        return path
    }
}

/// White mark and coral on-air dot in a `side`-point square. With `pulse`, a ring keeps leaving
/// the dot like an "ON AIR" lamp.
struct BrandMarkView: View {
    var side: CGFloat = 120
    var pulse = false

    var body: some View {
        let layout = BrandMarkLayout(side: side)
        ZStack {
            BrandMarkStroke()
                .stroke(.white, style: StrokeStyle(lineWidth: layout.lineWidth, lineCap: .round, lineJoin: .round))
            Circle()
                .stroke(Color.brandAccent, lineWidth: 2)
                .frame(width: layout.dotDiameter, height: layout.dotDiameter)
                .scaleEffect(pulse ? 3.2 : 1)
                .opacity(pulse ? 0 : 0.9)
                .animation(pulse ? .easeOut(duration: 1.2).repeatForever(autoreverses: false) : nil, value: pulse)
                .position(layout.dotCenter)
            Circle()
                .fill(Color.brandAccent)
                .frame(width: layout.dotDiameter, height: layout.dotDiameter)
                .position(layout.dotCenter)
        }
        .frame(width: side, height: side)
    }
}

/// The launch screen as a view: what shows until the start route is known, so the hand-off from
/// `UILaunchScreen` has no white frame and no jump.
struct LaunchFrame: View {
    var body: some View {
        ZStack {
            Color("LaunchBackground").ignoresSafeArea()
            BrandMarkView()
        }
        // Centred on the whole screen like the launch image (UIImageRespectsSafeAreaInsets = NO).
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}

// MARK: - Intro

/// Short brand intro before Onboarding (about 2.5 s; a tap skips it). It starts on the exact
/// launch frame, then the on-air dot pulses, the mark rises, "TSPodcast" and the tagline come
/// in and a soft waveform moves at the bottom. Reduce Motion: no movement, a fade only.
struct BrandIntroView: View {
    let onFinished: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pulse = false
    @State private var lifted = false
    @State private var showWordmark = false
    @State private var showTagline = false
    @State private var finished = false

    /// Light blue of "TS" on dark backgrounds (BRAND §1, `tspodcast-lockup-*-dark`).
    private static let wordmarkTS = Color(red: 0x8F / 255, green: 0xB0 / 255, blue: 1)
    /// Xanh Sáng `#3D7BFF`, the glow behind the mark.
    private static let glow = Color(red: 0x3D / 255, green: 0x7B / 255, blue: 1)

    var body: some View {
        ZStack {
            Color("LaunchBackground").ignoresSafeArea()
            RadialGradient(colors: [Self.glow.opacity(0.5), .clear], center: .center, startRadius: 0, endRadius: 380)
                .ignoresSafeArea()
                .opacity(lifted ? 1 : 0)

            BrandMarkView(pulse: pulse && !reduceMotion)
                .scaleEffect(lifted ? 0.8 : 1)
                .offset(y: lifted ? -64 : 0)

            // Two Texts side by side, not `Text + Text` (deprecated from the iOS 26 SDK).
            HStack(spacing: 0) {
                Text(verbatim: "TS").foregroundStyle(Self.wordmarkTS)
                Text(verbatim: "Podcast").foregroundStyle(.white)
            }
                .font(.system(size: 40, weight: .heavy, design: .rounded))
                .offset(y: showWordmark ? 22 : 36)
                .opacity(showWordmark ? 1 : 0)

            Text("intro.tagline")
                .font(.system(size: 20, weight: .semibold, design: .rounded))
                .foregroundStyle(.white.opacity(0.92))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
                .offset(y: showTagline ? 70 : 82)
                .opacity(showTagline ? 1 : 0)

            VStack {
                Spacer()
                IntroWaveform(animated: !reduceMotion)
                    .padding(.bottom, 64)
                    .opacity(showWordmark ? 1 : 0)
            }
        }
        .ignoresSafeArea()
        .contentShape(Rectangle())
        .onTapGesture(perform: finish)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "TSPodcast"))
        .accessibilityValue(Text("intro.tagline"))
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("intro")
        .task { await play() }
    }

    private func play() async {
        if reduceMotion {
            withAnimation(.easeOut(duration: 0.4)) {
                showWordmark = true
                showTagline = true
            }
            guard await wait(1_600) else { return }
        } else {
            pulse = true
            guard await wait(250) else { return }
            withAnimation(.spring(response: 0.6, dampingFraction: 0.82)) { lifted = true }
            guard await wait(250) else { return }
            withAnimation(.easeOut(duration: 0.5)) { showWordmark = true }
            guard await wait(250) else { return }
            withAnimation(.easeOut(duration: 0.5)) { showTagline = true }
            guard await wait(1_600) else { return }
        }
        finish()
    }

    /// Sleeps `milliseconds`; false when the intro went away meanwhile (tap, or the view left).
    private func wait(_ milliseconds: Int) async -> Bool {
        try? await Task.sleep(for: .milliseconds(milliseconds))
        return !Task.isCancelled && !finished
    }

    private func finish() {
        guard !finished else { return }
        finished = true
        onFinished()
    }
}

/// Bars of a sound wave under the intro, highest in the middle, moving gently.
struct IntroWaveform: View {
    var animated = true
    private let count = 25

    var body: some View {
        TimelineView(.animation(minimumInterval: nil, paused: !animated)) { context in
            let time = context.date.timeIntervalSinceReferenceDate
            HStack(alignment: .center, spacing: 6) {
                ForEach(0..<count, id: \.self) { index in
                    Capsule()
                        .fill(.white.opacity(0.18))
                        .frame(width: 6, height: height(index, time: animated ? time : 0))
                }
            }
            .frame(height: 72)
        }
        .accessibilityHidden(true)
    }

    private func height(_ index: Int, time: Double) -> CGFloat {
        let envelope = sin(Double.pi * (Double(index) + 0.5) / Double(count))
        let wave = 0.55 + 0.45 * sin(time * 5 + Double(index) * 0.55)
        return CGFloat(max(6, 64 * envelope * wave))
    }
}
