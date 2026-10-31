package tss.t.shared.usecase

import kotlinx.serialization.SerializationException
import tss.t.shared.model.TSDataState
import tss.t.shared.network.MissingCredentialsException

/**
 * Retry rules of Android's `PodcastsRepositoryImpl` (`retryWhen { attempt < 3 }`): up to
 * [maxRetries] more requests after the first one, immediately, with no delay in between.
 * Only E10 and E11 retry; every other endpoint makes exactly one request.
 *
 * Missing credentials are never retried: no request was sent and none would succeed.
 */
internal sealed class RetryPolicy(val maxRetries: Int) {
    abstract fun shouldRetry(cause: Throwable): Boolean

    /** E10 `episodes/byfeedid`: any error. */
    data object AnyError : RetryPolicy(maxRetries = 3) {
        override fun shouldRetry(cause: Throwable): Boolean = cause !is MissingCredentialsException
    }

    /**
     * E11 `podcasts/byfeedid`: HTTP and IO errors. A body that does not parse, or an
     * `IllegalStateException` (Android's guard), would fail the same way again.
     * [MissingCredentialsException] is an `IllegalStateException`, so it is excluded too.
     */
    data object NetworkErrors : RetryPolicy(maxRetries = 3) {
        override fun shouldRetry(cause: Throwable): Boolean =
            cause !is IllegalStateException && cause !is SerializationException
    }
}

/**
 * Runs [block] until it returns something other than `Error`, or until [policy] gives up.
 * The last `Error` is returned as is. `CancellationException` is never caught here.
 */
internal suspend fun <T : Any> withRetry(
    policy: RetryPolicy,
    block: suspend () -> TSDataState<T>,
): TSDataState<T> {
    var attempt = 0
    while (true) {
        val result = block()
        if (result !is TSDataState.Error) return result
        if (attempt >= policy.maxRetries || !policy.shouldRetry(result.exception)) return result
        attempt++
    }
}
