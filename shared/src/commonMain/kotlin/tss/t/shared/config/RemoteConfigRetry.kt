package tss.t.shared.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Backoff for Remote Config's `fetchAndActivate` at launch, from Android
 * `TSFirebaseRemoteConfig.setup()` (features-spec §1: up to 20 tries, 30 s × n between them).
 *
 * Attempts are numbered from 1. After failed attempt `n` the caller waits [delayFor]`(n)` and
 * tries again while [hasAttemptAfter]`(n)`, so at most [MAX_ATTEMPTS] fetches are made in about
 * 95 minutes. Android's loop can make two more (it also fetches once before the loop and
 * checks `failCount <= 20`); 20 is the documented rule.
 */
object RemoteConfigRetry {
    const val MAX_ATTEMPTS = 20

    /** Step of the linear backoff. */
    val STEP: Duration = 30.seconds

    /** Wait after failed attempt [attempt] (≥ 1): 30 s, 60 s, 90 s, … */
    fun delayFor(attempt: Int): Duration {
        require(attempt >= 1) { "attempt starts at 1, was $attempt" }
        return STEP * attempt
    }

    /** [delayFor] in milliseconds, for Swift (a Kotlin `Duration` does not cross the bridge). */
    fun delayMillisFor(attempt: Int): Long = delayFor(attempt).inWholeMilliseconds

    /** True when another attempt may follow failed attempt [attempt]. */
    fun hasAttemptAfter(attempt: Int): Boolean = attempt in 1 until MAX_ATTEMPTS
}
