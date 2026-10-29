package tss.t.shared.analytics

/**
 * Seam for analytics (Android: `trackScreen` / `trackEvent` on Firebase Analytics). Until a
 * Firebase iOS app exists, the Swift side logs the calls. Never carries a `device_id`
 * (owner decision: Install ID only).
 */
interface AnalyticsTracker {
    /** A screen was shown, e.g. `trackScreen("Onboarding", mapOf("CurrentPage" to "2"))`. */
    fun trackScreen(screen: String, params: Map<String, String>)

    /** An event happened on [screen], e.g. `trackEvent("OnboardingFinished", "Onboarding")`. */
    fun trackEvent(name: String, screen: String)
}
