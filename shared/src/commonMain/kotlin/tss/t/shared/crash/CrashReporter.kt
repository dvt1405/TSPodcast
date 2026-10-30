package tss.t.shared.crash

import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * Crash-reporting seam, port of Android `sharedLibrary/crash/CrashReporter`. The app installs a
 * Crashlytics-backed reporter when Firebase is configured; until then [Crash] logs.
 */
interface CrashReporter {

    /** Breadcrumb attached to the next crash. Not itself an error. */
    fun log(message: String)

    /**
     * Report a handled (non-fatal) throwable.
     *
     * @param tag stable identifier for the call site, so the same failure groups together.
     * @param keys extra context recorded with this one report.
     */
    fun record(throwable: Throwable, tag: String, keys: Map<String, String> = emptyMap())

    fun key(name: String, value: String)

    fun key(name: String, value: Long)

    fun key(name: String, value: Boolean)

    fun userId(id: String)
}

/** Fallback before [Crash.install] and in tests: prints, so a missing install is visible. */
object LogCrashReporter : CrashReporter {
    override fun log(message: String) = println("CrashReporter: $message")

    override fun record(throwable: Throwable, tag: String, keys: Map<String, String>) =
        println("CrashReporter: [$tag] $keys ${throwable::class.simpleName}")

    override fun key(name: String, value: String) = Unit
    override fun key(name: String, value: Long) = Unit
    override fun key(name: String, value: Boolean) = Unit
    override fun userId(id: String) = Unit
}

/** Process-global facade (Android `Crash`): for call sites that cannot be injected. */
object Crash : CrashReporter {

    @Volatile
    private var delegate: CrashReporter = LogCrashReporter

    /** Called once at launch, after Firebase is configured (or not). */
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

/**
 * Android `safeCall`: like [runCatching], but reports the throwable to [reporter] and rethrows
 * [CancellationException] so structured concurrency keeps working.
 */
inline fun <T> safeCall(
    tag: String,
    keys: Map<String, String> = emptyMap(),
    reporter: CrashReporter = Crash,
    block: () -> T,
): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    reporter.record(throwable, tag, keys)
    Result.failure(throwable)
}
