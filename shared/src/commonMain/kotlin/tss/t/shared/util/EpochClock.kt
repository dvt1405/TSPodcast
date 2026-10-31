package tss.t.shared.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Wall-clock time in epoch milliseconds. Injected wherever time matters (request signing,
 * cache TTL) so tests can pin it.
 */
fun interface EpochClock {
    fun nowMillis(): Long

    companion object {
        @OptIn(ExperimentalTime::class)
        val System: EpochClock = EpochClock { Clock.System.now().toEpochMilliseconds() }
    }
}
