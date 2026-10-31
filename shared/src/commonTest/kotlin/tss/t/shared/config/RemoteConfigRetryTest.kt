package tss.t.shared.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** M4-AC11: Android `TSFirebaseRemoteConfig.setup()` backoff (features-spec §1). */
class RemoteConfigRetryTest {

    @Test
    fun twentyAttemptsWithLinearBackoff() {
        assertEquals(20, RemoteConfigRetry.MAX_ATTEMPTS)
        assertEquals(30.seconds, RemoteConfigRetry.delayFor(1))
        assertEquals(60.seconds, RemoteConfigRetry.delayFor(2))
        assertEquals(90.seconds, RemoteConfigRetry.delayFor(3))
        assertEquals(90_000L, RemoteConfigRetry.delayMillisFor(3))
    }

    @Test
    fun attemptsStopAfterTheTwentieth() {
        val attempts = generateSequence(1) { if (RemoteConfigRetry.hasAttemptAfter(it)) it + 1 else null }.toList()

        assertEquals((1..20).toList(), attempts)
        assertFalse(RemoteConfigRetry.hasAttemptAfter(20))
        assertFalse(RemoteConfigRetry.hasAttemptAfter(0))
        assertTrue(RemoteConfigRetry.hasAttemptAfter(19))
        // Waits between the 20 attempts: 30 s × (1 + … + 19) = 95 minutes.
        assertEquals(95.minutes, (1 until 20).map { RemoteConfigRetry.delayFor(it) }.reduce { a, b -> a + b })
    }

    @Test
    fun attemptStartsAtOne() {
        assertFailsWith<IllegalArgumentException> { RemoteConfigRetry.delayFor(0) }
    }
}
