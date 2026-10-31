package tss.t.shared.crash

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import tss.t.shared.TestGraph
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexHttpException
import tss.t.shared.network.respondJson
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** M4-AC8: Android `sharedLibrary/crash` (`Crash`, `safeCall`) and its use in `GetMusicFeeds`. */
class CrashTest {

    @AfterTest
    fun restoreDefault() {
        Crash.install(LogCrashReporter)
    }

    @Test
    fun facadeWorksBeforeInstall() {
        Crash.install(LogCrashReporter)

        Crash.log("before install")
        Crash.record(IllegalStateException("x"), "Tag")
        Crash.key("k", "v")
        Crash.key("k", 1L)
        Crash.key("k", true)
        Crash.userId("id")
        Crash.recordFatal(IllegalStateException("fatal"), listOf(1L))
    }

    @Test
    fun everyCallReachesTheInstalledReporter() {
        val fake = FakeCrashReporter()
        Crash.install(fake)
        val error = IllegalArgumentException("boom")
        val fatal = IllegalStateException("fatal")

        Crash.log("breadcrumb")
        Crash.record(error, "Tag", mapOf("a" to "b"))
        Crash.key("s", "v")
        Crash.key("l", 2L)
        Crash.key("b", true)
        Crash.userId("install-id")
        Crash.recordFatal(fatal, listOf(10L, 20L))

        assertEquals(listOf("breadcrumb"), fake.logs)
        assertEquals(listOf(FakeCrashReporter.Record(error, "Tag", mapOf("a" to "b"))), fake.records)
        assertEquals(listOf<Pair<String, Any>>("s" to "v", "l" to 2L, "b" to true), fake.keys)
        assertEquals(listOf("install-id"), fake.userIds)
        assertEquals(listOf<Pair<Throwable, List<Long>>>(fatal to listOf(10L, 20L)), fake.fatals)
    }

    @Test
    fun safeCallReturnsTheValueAndReportsNothing() {
        val fake = FakeCrashReporter()

        val result = safeCall("Tag", reporter = fake) { 42 }

        assertEquals(42, result.getOrNull())
        assertTrue(fake.records.isEmpty())
    }

    @Test
    fun safeCallSwallowsAndRecordsWithTagAndKeys() {
        val fake = FakeCrashReporter()
        val error = IllegalStateException("bad")

        val result = safeCall<Unit>("Tag", mapOf("k" to "v"), reporter = fake) { throw error }

        assertSame(error, result.exceptionOrNull())
        assertEquals(listOf(FakeCrashReporter.Record(error, "Tag", mapOf("k" to "v"))), fake.records)
    }

    @Test
    fun safeCallUsesTheFacadeByDefault() {
        val fake = FakeCrashReporter()
        Crash.install(fake)

        safeCall<Unit>("Facade") { error("x") }

        assertEquals(listOf("Facade"), fake.records.map { it.tag })
    }

    @Test
    fun safeCallOrNullAndOrDefault() {
        val fake = FakeCrashReporter()

        assertNull(safeCallOrNull<String>("A", reporter = fake) { error("x") })
        assertEquals("fallback", safeCallOr("B", "fallback", reporter = fake) { error("y") })
        assertEquals("ok", safeCallOr("C", "fallback", reporter = fake) { "ok" })
        assertEquals(listOf("A", "B"), fake.records.map { it.tag })
    }

    @Test
    fun safeCallNeverSwallowsCancellation() {
        val fake = FakeCrashReporter()

        assertFailsWith<CancellationException> {
            safeCall<Unit>("Tag", reporter = fake) { throw CancellationException("cancelled") }
        }
        assertFailsWith<CancellationException> {
            safeCallOrNull<Unit>("Tag", reporter = fake) { throw CancellationException("cancelled") }
        }
        assertTrue(fake.records.isEmpty())
    }

    @Test
    fun getMusicFeedsFailureIsRecordedWithMediumKey() = runTest {
        val test = TestGraph { respondJson("""{"status":"false"}""", HttpStatusCode.InternalServerError) }

        val result = test.graph.getMusicFeeds()

        val error = assertIs<TSDataState.Error<*>>(result)
        assertEquals(emptyList<Any>(), error.data)
        val record = test.crash.records.single()
        assertIs<PodcastIndexHttpException>(record.throwable)
        assertEquals("GetMusicFeeds", record.tag)
        assertEquals(mapOf("medium" to "music"), record.keys)
    }

    @Test
    fun getMusicFeedsSuccessReportsNothing() = runTest {
        val test = TestGraph { respondJson("""{"count":1,"feeds":[{"id":1,"title":"A"}]}""") }

        val result = test.graph.getMusicFeeds()

        assertIs<TSDataState.Success<*>>(result)
        assertTrue(test.crash.records.isEmpty())
    }
}
