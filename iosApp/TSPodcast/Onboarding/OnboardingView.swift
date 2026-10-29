import SwiftUI

/// Onboarding (features-spec §3.1). M1 T2 scaffold: the first page only, so `RootView` can route
/// here; T3 replaces it with the 4-page pager, highlights, dots, "Tiếp tục" and analytics.
struct OnboardingView: View {
    let onFinished: () -> Void

    var body: some View {
        VStack(spacing: 32) {
            Image("onboarding_slide_2")
                .resizable()
                .scaledToFit()
                .padding(.horizontal, 24)
            Text("onboarding.title.1")
                .font(.largeTitle.bold())
                .foregroundStyle(Color(red: 0x39 / 255, green: 0x37 / 255, blue: 0x5B / 255))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(red: 0xEA / 255, green: 0xF0 / 255, blue: 0xE4 / 255).ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("onboarding.page.0")
    }
}
