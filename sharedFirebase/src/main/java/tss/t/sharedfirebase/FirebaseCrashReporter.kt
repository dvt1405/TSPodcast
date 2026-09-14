package tss.t.sharedfirebase

import com.google.firebase.crashlytics.FirebaseCrashlytics
import tss.t.sharedlibrary.crash.CrashReporter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-backed [CrashReporter]. Bound in [AbsFirebaseModule] and installed
 * into the [tss.t.sharedlibrary.crash.Crash] facade from `App.onCreate`.
 *
 * Every method is defensive: Crashlytics is initialised by a ContentProvider,
 * and a reporting failure must never become the crash it was meant to report.
 */
@Singleton
class FirebaseCrashReporter @Inject constructor() : CrashReporter {

    private val crashlytics: FirebaseCrashlytics?
        get() = try {
            FirebaseCrashlytics.getInstance()
        } catch (_: Throwable) {
            null
        }

    override fun log(message: String) {
        crashlytics?.log(message)
    }

    override fun record(throwable: Throwable, tag: String, keys: Map<String, String>) {
        val instance = crashlytics ?: return
        // Recorded as a key rather than only a log so that non-fatals from the
        // same call site can be filtered in the console.
        instance.setCustomKey(KEY_TAG, tag)
        keys.forEach { (name, value) -> instance.setCustomKey(name, value) }
        instance.log("[$tag] ${throwable.javaClass.simpleName}: ${throwable.message}")
        instance.recordException(throwable)
    }

    override fun key(name: String, value: String) {
        crashlytics?.setCustomKey(name, value)
    }

    override fun key(name: String, value: Long) {
        crashlytics?.setCustomKey(name, value)
    }

    override fun key(name: String, value: Boolean) {
        crashlytics?.setCustomKey(name, value)
    }

    override fun userId(id: String) {
        crashlytics?.setUserId(id)
    }

    companion object {
        private const val KEY_TAG = "report_tag"
    }
}
