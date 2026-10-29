package tss.t.shared.usecase

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import tss.t.shared.FakeConfig
import tss.t.shared.TestGraph
import tss.t.shared.model.TSDataState
import tss.t.shared.model.TrendingPodcastRes
import tss.t.shared.network.FixedClock
import tss.t.shared.network.respondJson
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.preferences.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** M1-AC5: `cachedTrending*` / `lastUpdateTrending*` on disk, 15 minutes, like Android. */
class TrendingDiskCacheTest {

    private val body = """{"count":2,"feeds":[{"id":5,"title":"Clean"},{"id":6,"title":"Blocked show"}]}"""

    private fun graph(
        store: InMemoryKeyValueStore,
        at: Duration = Duration.ZERO,
        status: () -> HttpStatusCode = { HttpStatusCode.OK },
    ) = TestGraph(
        store = store,
        clock = FixedClock(TestGraph.START_MILLIS + at.inWholeMilliseconds),
        handler = { respondJson(body, status()) },
    )

    private fun TestGraph.requestsFor(cat: String?): Int = requests.count {
        it.url.encodedPath.endsWith("/podcasts/trending") && it.url.parameters["cat"] == cat
    }

    private suspend fun TestGraph.trending(cat: String?) =
        graph.getTrendingPodcasts(max = 100, since = 1L, cat = cat)

    @Test
    fun graphAWritesBothKeysPerCategory() = runTest {
        val store = InMemoryKeyValueStore()
        val a = graph(store)

        a.trending(null)
        a.trending("1,2,3")

        val keys = store.snapshot().keys
        assertTrue(
            keys.containsAll(
                listOf("cachedTrending", "lastUpdateTrending", "cachedTrending_1,2,3", "lastUpdateTrending_1,2,3"),
            ),
            "keys: $keys",
        )
        assertEquals(TestGraph.START_MILLIS, store.getLong("lastUpdateTrending"))
        assertEquals(TestGraph.START_MILLIS, store.getLong("lastUpdateTrending_1,2,3"))
    }

    @Test
    fun graphBUsesTheDiskCopyUntilFifteenMinutes() = runTest {
        val store = InMemoryKeyValueStore()
        val a = graph(store)
        val fromNetwork = a.trending(null)
        a.trending("1,2,3")

        val fresh = graph(store, at = 14.minutes + 59.seconds)
        assertEquals(fromNetwork, fresh.trending(null))
        assertIs<TSDataState.Success<TrendingPodcastRes>>(fresh.trending("1,2,3"))
        assertEquals(0, fresh.requests.size)

        val stale = graph(store, at = 15.minutes + 1.seconds)
        stale.trending(null)
        stale.trending("1,2,3")
        assertEquals(1, stale.requestsFor(null))
        assertEquals(1, stale.requestsFor("1,2,3"))
    }

    @Test
    fun aDiskCopyLoadedIntoMemoryKeepsItsOriginalAge() = runTest {
        val store = InMemoryKeyValueStore()
        graph(store).trending(null)

        val b = graph(store, at = 10.minutes)
        b.trending(null)
        b.advance(5.minutes + 1.seconds) // 15:01 after graph A stored it
        b.trending(null)

        assertEquals(1, b.requestsFor(null))
    }

    @Test
    fun errorsAreNotPersisted() = runTest {
        val store = InMemoryKeyValueStore()
        val a = graph(store) { HttpStatusCode.InternalServerError }

        assertIs<TSDataState.Error<*>>(a.trending(null))
        assertIs<TSDataState.Error<*>>(a.trending("1,2,3"))

        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun corruptDiskCopyFallsThroughToTheNetwork() = runTest {
        val store = InMemoryKeyValueStore()
        store.putString("cachedTrending", "{\"count\": oops")
        store.putLong("lastUpdateTrending", TestGraph.START_MILLIS)
        val b = graph(store, at = 1.minutes)

        val result = assertIs<TSDataState.Success<TrendingPodcastRes>>(b.trending(null))

        assertEquals(1, b.requestsFor(null))
        assertEquals(listOf(5L, 6L), result.data.items.map { it.id })
        assertNotNull(b.graph.appPreferences.cachedTrending(null), "the network result replaces it")
    }

    @Test
    fun theDiskCopyIsBlacklistFiltered() = runTest {
        val store = InMemoryKeyValueStore()
        val a = TestGraph(
            store = store,
            config = FakeConfig.blacklist("""{"feedId":["6"]}"""),
            handler = { respondJson(body) },
        )

        a.trending(null)

        val saved = assertNotNull(store.getString("cachedTrending"))
        assertTrue(saved.contains("Clean"))
        assertFalse(saved.contains("Blocked show"))
        val restored = a.graph.appPreferences.cachedTrending(null)
        assertEquals(listOf(5L), restored?.value?.items?.map { it.id })
        assertEquals(1, restored?.value?.count)
        assertNull(store.getString("cachedTrending_"))
    }
}
