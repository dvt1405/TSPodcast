package tss.t.shared.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** M4-AC8: the uncaught-exception hook reports throwable and stack (without ending the test). */
class KotlinCrashHookTest {

    private fun thrown(): Throwable = try {
        throw IllegalStateException("uncaught")
    } catch (e: IllegalStateException) {
        e
    }

    @Test
    fun handlerSendsTheThrowableAndItsStackAddresses() {
        val fake = FakeCrashReporter()
        val error = thrown()

        kotlinCrashHandler(fake)(error)

        val (throwable, addresses) = fake.fatals.single()
        assertSame(error, throwable)
        assertTrue(addresses.isNotEmpty(), "no stack addresses")
        assertTrue(addresses.all { it != 0L })
        assertTrue(fake.records.isEmpty(), "a fatal is not also a non-fatal record")
    }

    @Test
    fun aReporterThatThrowsDoesNotEscapeTheHandler() {
        val throwing = object : CrashReporter by FakeCrashReporter() {
            override fun recordFatal(throwable: Throwable, stackAddresses: List<Long>) {
                error("reporter broke")
            }
        }

        kotlinCrashHandler(throwing)(thrown())
    }

    @Test
    fun handlerGoesThroughTheFacade() {
        val fake = FakeCrashReporter()
        Crash.install(fake)
        try {
            kotlinCrashHandler(Crash)(thrown())
        } finally {
            Crash.install(LogCrashReporter)
        }

        assertEquals(1, fake.fatals.size)
    }
}
