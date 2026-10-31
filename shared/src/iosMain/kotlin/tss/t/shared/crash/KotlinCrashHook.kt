package tss.t.shared.crash

import kotlin.experimental.ExperimentalNativeApi

/**
 * Sends Kotlin exceptions that nobody caught to [reporter] before the process ends (M4 §4.2,
 * plan §7 item 7: a hand-written hook instead of CrashKiOS).
 *
 * Kotlin/Native calls the hook when an exception reaches the edge of Kotlin code (a Swift call
 * into a function without `@Throws`, a top-level coroutine, a Kotlin thread) and terminates the
 * process right after it returns, so the hook only reports and never tries to recover. Without
 * it the crash shows up in Crashlytics as a bare `abort()` with no Kotlin type or message.
 *
 * Call once at launch, after `Crash.install`. From Swift:
 * `KotlinCrashHookKt.installKotlinCrashHook(reporter: Crash.shared)`.
 */
@OptIn(ExperimentalNativeApi::class)
fun installKotlinCrashHook(reporter: CrashReporter) {
    val handler = kotlinCrashHandler(reporter)
    var previous: ReportUnhandledExceptionHook? = null
    previous = setUnhandledExceptionHook { throwable ->
        handler(throwable)
        // Another hook installed earlier (none today) still gets its turn.
        previous?.let { runCatching { it(throwable) } }
    }
}

/**
 * The body of the hook, separate so tests can call it without ending the process: reports
 * [Throwable] plus its raw stack addresses. Never throws, since an exception thrown from the
 * hook would hide the original one.
 */
@OptIn(ExperimentalNativeApi::class)
internal fun kotlinCrashHandler(reporter: CrashReporter): (Throwable) -> Unit = { throwable ->
    runCatching { reporter.recordFatal(throwable, throwable.getStackTraceAddresses()) }
}
