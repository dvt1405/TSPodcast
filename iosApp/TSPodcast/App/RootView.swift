import SwiftUI

/// Switches between Onboarding, category selection and Main. The start route is read once per
/// launch; until it is known (a few ms) only the plain background shows, without a spinner.
struct RootView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var route: AppRoute?

    var body: some View {
        content
            .animation(.default, value: route)
            .task {
                guard route == nil else { return }
                route = await environment.resolveStartRoute()
            }
    }

    @ViewBuilder
    private var content: some View {
        switch route {
        case nil:
            Color(.systemBackground)
                .ignoresSafeArea()
        case .onboarding:
            OnboardingView(onFinished: { route = .selectCategories })
        case .selectCategories:
            CategorySelectionView(onSaved: { route = .main })
        case .main:
            MainTabView()
        }
    }
}
