package tss.t.sharedfirebase

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import tss.t.sharedlibrary.analytics.AnalyticsEvent
import tss.t.sharedlibrary.analytics.AnalyticsTracker
import tss.t.sharedlibrary.analytics.AppEvents
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Analytics behind [AnalyticsTracker]. Bound in [AbsFirebaseModule] and installed
 * into [tss.t.sharedlibrary.analytics.Analytics] from `App.onCreate`.
 *
 * Consent: [PrivacyConsent] already switches Firebase collection on and off; this class also
 * drops events itself while usage analytics is off, so nothing is queued or computed for a
 * user who said no. User properties are remembered and sent again when consent is given
 * later in Settings, so they are never missing on the first events after opting in.
 */
@Singleton
class FirebaseAnalyticsTracker @Inject constructor(
    @ApplicationContext context: Context,
    private val consent: PrivacyConsent,
    @FirebaseScope(FirebaseDispatcher.Default)
    scope: CoroutineScope,
) : AnalyticsTracker {

    private val userProperties = LinkedHashMap<String, String?>()

    /** Debug builds echo every event to logcat (tag `TSAnalytics`), consent or not. */
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @Volatile
    override var currentScreen: String? = null
        private set

    private val enabled: Boolean
        get() = consent.choices.value.analytics

    init {
        scope.launch {
            consent.choices
                .distinctUntilChangedBy { it.analytics }
                .collect { if (it.analytics) flushUserProperties() }
        }
    }

    override fun log(event: AnalyticsEvent) {
        if (debuggable) Log.d(TAG, "${if (enabled) "log" else "drop (no consent)"} $event")
        if (!enabled) return
        runCatching { Firebase.analytics.logEvent(event.name, event.toBundle()) }
            .onFailure { Log.w(TAG, "logEvent ${event.name} failed", it) }
    }

    override fun setUserProperty(name: String, value: String?) {
        val trimmed = value?.take(AppEvents.UserProperty.MAX_VALUE)
        synchronized(userProperties) { userProperties[name] = trimmed }
        if (debuggable) Log.d(TAG, "user property $name=$trimmed (consent=$enabled)")
        if (!enabled) return
        runCatching { Firebase.analytics.setUserProperty(name, trimmed) }
    }

    override fun screen(screenName: String, screenClass: String) {
        currentScreen = screenName
        log(AppEvents.screenView(screenName, screenClass))
    }

    private fun flushUserProperties() {
        val snapshot = synchronized(userProperties) { userProperties.toMap() }
        snapshot.forEach { (name, value) ->
            runCatching { Firebase.analytics.setUserProperty(name, value) }
        }
    }

    private companion object {
        const val TAG = "TSAnalytics"
    }
}

/** GA4 bundle: strings, longs and doubles only ([AnalyticsEvent.of] already normalised them). */
internal fun AnalyticsEvent.toBundle(): Bundle = Bundle().apply {
    params.forEach { (key, value) ->
        when (value) {
            is String -> putString(key, value)
            is Long -> putLong(key, value)
            is Double -> putDouble(key, value)
            else -> putString(key, value.toString())
        }
    }
}
