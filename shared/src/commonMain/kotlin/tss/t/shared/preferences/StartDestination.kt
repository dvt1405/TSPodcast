package tss.t.shared.preferences

/** First screen at launch, as `TSNavGraph` picks its `startDestination` (features-spec §1). */
enum class StartDestination {
    Onboarding,
    SelectFavouriteCategory,
    Main,
}

/**
 * `OnboardingFinished` is checked first: a store with only `HasSelectFavouriteCategory`
 * still starts at onboarding, like Android.
 */
suspend fun AppPreferences.startDestination(): StartDestination = when {
    !isOnboardingFinished() -> StartDestination.Onboarding
    !hasSelectFavouriteCategory() -> StartDestination.SelectFavouriteCategory
    else -> StartDestination.Main
}
