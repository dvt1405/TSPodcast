package tss.t.shared.analytics

/**
 * Seam for analytics (Android `TSAnalytics.trackScreen` / `trackEvent` on Firebase Analytics).
 * The Swift side logs the calls until a Firebase iOS app exists, then sends them to Firebase
 * with [AnalyticsDefaults]. Never carries a device identifier (D5: Install ID only).
 */
interface AnalyticsTracker {
    /**
     * A screen was shown, e.g. `trackScreen("Onboarding", mapOf("CurrentPage" to "2"))`.
     * Logged as the event [AnalyticsDefaults.screenEventName] (`onboarding_screen`).
     */
    fun trackScreen(screen: String, params: Map<String, String>)

    /**
     * An event happened on [screen], e.g. `trackEvent("OnboardingFinished", "Onboarding", emptyMap())`.
     * [screen] is null when no screen is known (a push received in the background, like
     * Android's `currentScreenName` being null); [params] go out with the default parameters.
     */
    fun trackEvent(name: String, screen: String?, params: Map<String, String>)
}
