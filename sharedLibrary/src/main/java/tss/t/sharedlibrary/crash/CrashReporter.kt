package tss.t.sharedlibrary.crash

import android.util.Log

/**
 * Crash-reporting seam.
 *
 * Lives in `sharedLibrary` rather than `sharedFirebase` so that modules which
 * must report exceptions (`core`, `coreRadio`, `podcasts`, `sharedPlayer`) do
 * not each take a direct dependency on Firebase. The Firebase-backed
 * implementation is bound in `sharedFirebase` and installed from `App.onCreate`.
 */
interface CrashReporter {

    /** Breadcrumb attached to the next crash. Not itself an error. */
    fun log(message: String)

    /**
     * Report a handled (non-fatal) throwable.
     *
     * @param tag stable identifier for the call site, so the same logical
     *   failure groups together in the console regardless of stack shape.
     * @param keys extra context recorded alongside this one report.
     */
    fun record(throwable: Throwable, tag: String, keys: Map<String, String> = emptyMap())

    fun key(name: String, value: String)

    fun key(name: String, value: Long)

    fun key(name: String, value: Boolean)

    fun userId(id: String)
}

/**
 * Fallback used before [Crash.install] runs, and in unit tests. Writes to
 * logcat so a missing installation is visible rather than silent.
 */
internal object LogcatCrashReporter : CrashReporter {
    private const val TAG = "CrashReporter"

    override fun log(message: String) {
        Log.d(TAG, message)
    }

    override fun record(throwable: Throwable, tag: String, keys: Map<String, String>) {
        Log.w(TAG, "[$tag] $keys", throwable)
    }

    override fun key(name: String, value: String) = Unit
    override fun key(name: String, value: Long) = Unit
    override fun key(name: String, value: Boolean) = Unit
    override fun userId(id: String) = Unit
}

/**
 * Process-global facade.
 *
 * A global is used deliberately: several of the sites that need to report are
 * `init {}` blocks, companion objects and static helpers where Hilt injection
 * is not available. Everything else should inject [CrashReporter] directly.
 */
object Crash : CrashReporter {

    @Volatile
    private var delegate: CrashReporter = LogcatCrashReporter

    /** Called once from `App.onCreate` after `FirebaseApp.initializeApp`. */
    fun install(reporter: CrashReporter) {
        delegate = reporter
    }

    override fun log(message: String) = delegate.log(message)

    override fun record(throwable: Throwable, tag: String, keys: Map<String, String>) =
        delegate.record(throwable, tag, keys)

    override fun key(name: String, value: String) = delegate.key(name, value)

    override fun key(name: String, value: Long) = delegate.key(name, value)

    override fun key(name: String, value: Boolean) = delegate.key(name, value)

    override fun userId(id: String) = delegate.userId(id)
}
