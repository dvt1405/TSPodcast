import SwiftUI

/// Switches between Onboarding, category selection and Main. The start route is read once per
/// launch; until it is known (a few ms) only the plain background shows, without a spinner. The
/// privacy sheet shows over the first screen when no consent is saved yet.
struct RootView: View {
    @Environment(AppEnvironment.self) private var environment
    @State private var route: AppRoute?

    var body: some View {
        let consent = environment.consent
        content
            .animation(.default, value: route)
            .task {
                guard route == nil else { return }
                route = await environment.resolveStartRoute()
            }
            // First launch (or a new consent text): over whatever screen comes first, never
            // instead of it (M6 §3.6).
            .sheet(isPresented: Binding(
                get: { route != nil && consent.isPromptPresented },
                set: { consent.isPromptPresented = $0 }
            )) {
                ConsentSheet(
                    consent: environment.consent,
                    privacyURL: SettingsModel.privacyURL(from: Bundle.main.infoDictionary ?? [:])
                )
            }
    }

    @ViewBuilder
    private var content: some View {
        switch route {
        case nil:
            Color(.systemBackground)
                .ignoresSafeArea()
        case .onboarding:
            OnboardingView(model: environment.makeOnboardingModel()) { route = .selectCategories }
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
