package tss.t.shared.flow

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import tss.t.shared.crash.Crash
import kotlin.coroutines.CoroutineContext

/** Stops what it was returned by. Calling [close] more than once is fine. */
fun interface Closeable {
    fun close()
}

/**
 * Collects [flow] on [context] and hands every value to [onEach] until the returned [Closeable]
 * is closed. Swift has no `Flow` (no SKIE), so this is how it observes shared streams; the iOS
 * overload without [context] delivers on the main queue. An error ends the stream: it is
 * reported to [Crash] instead of crashing the process.
 */
internal fun <T> watch(flow: Flow<T>, context: CoroutineContext, onEach: (T) -> Unit): Closeable {
    val scope = CoroutineScope(SupervisorJob() + context)
    scope.launch {
        try {
            flow.collect { onEach(it) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Crash.record(throwable, TAG)
        }
    }
    return Closeable { scope.cancel() }
}

private const val TAG = "watch"
