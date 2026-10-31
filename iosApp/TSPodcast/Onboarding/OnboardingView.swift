import SwiftUI

/// Onboarding (features-spec §3.1, `OnboardingScreen` on Android): 4 swipeable pages with the
/// Android backgrounds and illustrations, a highlighted title, a dot indicator and "Tiếp tục"
/// on the last page only. Starts the E9 category request as soon as it appears, so the category
/// grid is usually ready when the user gets there.
struct OnboardingView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var model: OnboardingModel
    let onFinished: () -> Void

    init(model: OnboardingModel, onFinished: @escaping () -> Void) {
        _model = State(initialValue: model)
        self.onFinished = onFinished
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            ZStack(alignment: .bottom) {
                TabView(selection: $model.currentPage) {
                    ForEach(model.pages) { page in
                        OnboardingPageView(page: page, topInset: insets.top)
                            .ignoresSafeArea()
                            .tag(page.index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .ignoresSafeArea()

                bottomBar
                    .padding(.bottom, insets.bottom + 20)
            }
            .ignoresSafeArea()
        }
        .onChange(of: model.currentPage, initial: true) { _, page in
            model.pageDidChange(to: page)
        }
        .task {
            environment.categoryCatalog.prefetch()
        }
    }

    /// Dots on the left part of the row, the button sliding in from the trailing edge on the
    /// last page (Android: `DotsIndicator` with weight 1 + `AnimatedVisibility`).
    private var bottomBar: some View {
        HStack(spacing: 0) {
            PageDots(count: model.pages.count, current: model.currentPage)
                .frame(maxWidth: .infinity)
            if model.showsContinue {
                Button {
                    Task {
                        if await model.finish() {
                            onFinished()
                        }
                    }
                } label: {
                    Text("onboarding.continue")
                }
                .buttonStyle(GradientButtonStyle(cornerRadius: .infinity))
                .disabled(model.isFinishing)
                .padding(.trailing, 16)
                .accessibilityIdentifier("onboarding.continue")
                .transition(.move(edge: .trailing).combined(with: .opacity))
            }
        }
        .frame(height: 50)
        .animation(.easeInOut(duration: 0.25), value: model.showsContinue)
    }
}

/// One page: background over the whole screen, the illustration centred at full width and the
/// title 20 pt under the status bar.
private struct OnboardingPageView: View {
    let page: OnboardingPage
    let topInset: CGFloat

    var body: some View {
        ZStack(alignment: .top) {
            background
            Image(page.imageName)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityHidden(true)
            Text(page.title())
                .font(.largeTitle.bold())
                .foregroundStyle(OnboardingPage.titleColor.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, topInset + 20)
                .padding(.horizontal, 20)
                .accessibilityAddTraits(.isHeader)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("onboarding.page.\(page.index)")
    }

    @ViewBuilder
    private var background: some View {
        switch page.background {
        case .color(let rgb):
            rgb.color
        case .gradient(let top, let bottom):
            LinearGradient(
                stops: [
                    .init(color: top.color, location: 0),
                    .init(color: top.color, location: 0.5),
                    .init(color: bottom.color, location: 0.75),
                    .init(color: bottom.color, location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        }
    }
}

/// Dot indicator in Primary; the current dot is stretched like Android's `ShiftIndicatorType`.
/// Hidden from VoiceOver: the paged `TabView` already announces the page.
private struct PageDots: View {
    let count: Int
    let current: Int

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<count, id: \.self) { index in
                Capsule()
                    .fill(OnboardingPage.highlightColor.color.opacity(index == current ? 1 : 0.3))
                    .frame(width: index == current ? 24 : 10, height: 10)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: current)
        .accessibilityHidden(true)
    }
}
