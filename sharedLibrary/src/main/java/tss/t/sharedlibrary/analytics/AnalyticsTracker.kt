package tss.t.sharedlibrary.analytics

import android.util.Log

/**
 * Analytics seam (docs/done/analytics/analytics-events.md).
 *
 * Lives in `sharedLibrary`, like [tss.t.sharedlibrary.crash.CrashReporter], so that `core`,
 * `podcasts`, `sharedPlayer` and `ads` can log without depending on Firebase. The Firebase
 * implementation is bound in `sharedFirebase` and installed into [Analytics] from
 * `App.onCreate`. Every implementation must respect the user's consent (Settings → Usage
 * analytics): the Firebase one drops events and user properties while it is off.
 *
 * Events are built by [AnalyticsEvent.of] / the catalogue in [AppEvents], which already trim
 * names, keys and values to the GA4 limits, so a caller can never make an event disappear.
 */
interface AnalyticsTracker {
    /** Logs one event. Safe from any thread. */
    fun log(event: AnalyticsEvent)

    /** Sets (or clears, with null) a GA4 user property. Name ≤ 24 chars, value ≤ 36 chars. */
    fun setUserProperty(name: String, value: String?)

    /**
     * A screen became visible: logs GA4 `screen_view` (`screen_name`, `screen_class`) and
     * remembers it as [currentScreen].
     */
    fun screen(screenName: String, screenClass: String = screenName)

    /** Last screen passed to [screen]; ad and error events carry it as `screen`. */
    val currentScreen: String?
}

/** Drops everything. Used before [Analytics.install] in tests and previews. */
object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun log(event: AnalyticsEvent) = Unit
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun screen(screenName: String, screenClass: String) = Unit
    override val currentScreen: String? = null
}

/** Fallback before installation: logcat only, so a missing install is visible in debug. */
internal object LogcatAnalyticsTracker : AnalyticsTracker {
    private const val TAG = "Analytics"

    @Volatile
    override var currentScreen: String? = null
        private set

    override fun log(event: AnalyticsEvent) {
        Log.d(TAG, "(not installed) $event")
    }

    override fun setUserProperty(name: String, value: String?) {
        Log.d(TAG, "(not installed) user property $name=$value")
    }

    override fun screen(screenName: String, screenClass: String) {
        currentScreen = screenName
        Log.d(TAG, "(not installed) screen $screenName")
    }
}

/**
 * Records everything in memory, for unit tests of code that logs (and for checking what a
 * flow sends without a device).
 */
class RecordingAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()
    val userProperties = linkedMapOf<String, String?>()
    val screens = mutableListOf<String>()

    override var currentScreen: String? = null
        private set

    override fun log(event: AnalyticsEvent) {
        synchronized(events) { events += event }
    }

    override fun setUserProperty(name: String, value: String?) {
        synchronized(userProperties) { userProperties[name] = value }
    }

    override fun screen(screenName: String, screenClass: String) {
        currentScreen = screenName
        synchronized(screens) { screens += screenName }
        log(AppEvents.screenView(screenName, screenClass))
    }

    fun names(): List<String> = synchronized(events) { events.map { it.name } }
}

/**
 * Process-global facade, like [tss.t.sharedlibrary.crash.Crash]: Composables, Player
 * listeners and OkHttp interceptors log through it without Hilt plumbing. Classes that are
 * already injected can inject [AnalyticsTracker] instead.
 */
object Analytics : AnalyticsTracker {

    @Volatile
    private var delegate: AnalyticsTracker = LogcatAnalyticsTracker

    /** Called once from `App.onCreate`, after the consent choices were applied. */
    fun install(tracker: AnalyticsTracker) {
        delegate = tracker
    }

    override fun log(event: AnalyticsEvent) = runCatching { delegate.log(event) }.let { }

    override fun setUserProperty(name: String, value: String?) =
        runCatching { delegate.setUserProperty(name, value) }.let { }

    override fun screen(screenName: String, screenClass: String) =
        runCatching { delegate.screen(screenName, screenClass) }.let { }

    override val currentScreen: String?
        get() = delegate.currentScreen
}
