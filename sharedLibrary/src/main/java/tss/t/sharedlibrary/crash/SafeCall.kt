package tss.t.sharedlibrary.crash

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but with the two properties this codebase needs and
 * `runCatching` does not have:
 *
 *  1. the throwable is reported to [Crash] instead of being swallowed, and
 *  2. [CancellationException] is rethrown.
 *
 * The second point matters everywhere: `runCatching` catches [Throwable], so
 * every existing `runCatching { }` around a suspending call silently absorbs
 * coroutine cancellation and breaks structured concurrency.
 *
 * @param tag stable identifier for this call site (see [CrashReporter.record]).
 */
inline fun <T> safeCall(
    tag: String,
    keys: Map<String, String> = emptyMap(),
    block: () -> T
): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Crash.record(throwable, tag, keys)
    Result.failure(throwable)
}

/** [safeCall] discarding the failure. Use when the caller has a null path already. */
inline fun <T> safeCallOrNull(
    tag: String,
    keys: Map<String, String> = emptyMap(),
    block: () -> T
): T? = safeCall(tag, keys, block).getOrNull()

/** [safeCall] with a fallback value. */
inline fun <T> safeCallOr(
    tag: String,
    default: T,
    keys: Map<String, String> = emptyMap(),
    block: () -> T
): T = safeCall(tag, keys, block).getOrDefault(default)

/**
 * Replacement for `!!` on a value that is expected to be non-null but has been
 * observed null in production. Reports the violation, then degrades instead of
 * killing the process.
 *
 * @param reason what was expected, in words — this is the only context the
 *   report will carry, since there is no throwable to read a stack from.
 */
inline fun <T : Any> T?.orReport(
    tag: String,
    reason: String,
    fallback: () -> T
): T = this ?: run {
    Crash.record(IllegalStateException("Unexpected null: $reason"), tag)
    fallback()
}

/**
 * Replacement for `List.first()` on parser/scraper output, where "empty" means
 * the remote markup changed rather than a programming error.
 */
fun <T> List<T>.firstOrReport(tag: String, reason: String): T? =
    firstOrNull() ?: run {
        Crash.record(NoSuchElementException("Empty list: $reason"), tag)
        null
    }

/** [firstOrReport] with a predicate, for `first { ... }` call sites. */
inline fun <T> List<T>.firstOrReport(
    tag: String,
    reason: String,
    predicate: (T) -> Boolean
): T? = firstOrNull(predicate) ?: run {
    Crash.record(NoSuchElementException("No match: $reason"), tag)
    null
}
