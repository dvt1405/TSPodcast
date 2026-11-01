package tss.t.podcast.analytics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tss.t.core.storage.ContentPrefs
import tss.t.core.storage.LanguagePrefs
import tss.t.core.storage.PodcastLocationPrefs
import tss.t.core.storage.UiPrefs
import tss.t.sharedfirebase.PrivacyConsent
import tss.t.sharedlibrary.analytics.AnalyticsEvent
import tss.t.sharedlibrary.analytics.AnalyticsTracker
import tss.t.sharedlibrary.analytics.AppEvents
import tss.t.sharedlibrary.analytics.AppEvents.UserProperty
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User properties and setting-change events, from the preference flows themselves rather
 * than from each screen: the podcast locations change from Settings and from the Home sheet,
 * shows are hidden from Detail, Home and Search. Started once from `App.onCreate`.
 *
 * The first value of each flow only sets the user property; every later value also logs the
 * change event (`podcast_locations_change`, `app_language_change`, `liquid_glass_toggle`,
 * `explicit_toggle`, `show_hidden` / `show_unhidden`, `crash_reports_toggle`).
 */
@Singleton
class SettingsAnalytics @Inject constructor(
    private val tracker: AnalyticsTracker,
    private val locationPrefs: PodcastLocationPrefs,
    private val languagePrefs: LanguagePrefs,
    private val uiPrefs: UiPrefs,
    private val contentPrefs: ContentPrefs,
    private val privacyConsent: PrivacyConsent,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start() {
        if (started) return
        started = true

        observe(locationPrefs.locations) { previous, locations ->
            val regions = locations.map { it.region }
            tracker.setUserProperty(UserProperty.PODCAST_LOCATIONS, regions.joinToString(","))
            tracker.setUserProperty(UserProperty.PRIMARY_LOCATION, regions.firstOrNull())
            previous?.let { AppEvents.podcastLocationsChange(regions, it.map { l -> l.region }) }
        }
        observe(languagePrefs.language) { previous, language ->
            tracker.setUserProperty(UserProperty.APP_LANGUAGE, language.tag)
            previous?.let { AppEvents.appLanguageChange(language.tag, it.tag) }
        }
        observe(uiPrefs.liquidGlass) { previous, on ->
            tracker.setUserProperty(UserProperty.LIQUID_GLASS, on.toString())
            previous?.let { AppEvents.liquidGlassToggle(on) }
        }
        observe(contentPrefs.allowExplicit) { previous, allowed ->
            tracker.setUserProperty(UserProperty.EXPLICIT_ALLOWED, allowed.toString())
            previous?.let { AppEvents.explicitToggle(allowed) }
        }
        scope.launch {
            var previous: Set<Long>? = null
            contentPrefs.hiddenShows.collect { list ->
                val ids = list.mapTo(LinkedHashSet()) { it.feedId }
                previous?.let { before ->
                    (ids - before).forEach { tracker.log(AppEvents.showHidden(it)) }
                    (before - ids).forEach { tracker.log(AppEvents.showUnhidden(it)) }
                }
                previous = ids
            }
        }
        // Turning usage analytics off cannot be logged (nothing is sent after it); crash
        // reports can, while analytics is on.
        observe(privacyConsent.choices) { previous, choices ->
            if (previous != null && previous.crashReports != choices.crashReports) {
                AppEvents.crashReportsToggle(choices.crashReports)
            } else {
                null
            }
        }
    }

    /**
     * Collects [flow]; [onValue] gets the previous value (null the first time) and the new
     * one, sets what it needs and returns the event to log, if any.
     */
    private fun <T> observe(flow: StateFlow<T>, onValue: (previous: T?, value: T) -> AnalyticsEvent?) {
        scope.launch {
            var previous: T? = null
            var first = true
            flow.collect { value ->
                val event = onValue(if (first) null else previous, value)
                if (!first && event != null && previous != value) tracker.log(event)
                previous = value
                first = false
            }
        }
    }
}
