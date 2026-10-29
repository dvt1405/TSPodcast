package tss.t.shared.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import tss.t.shared.TestGraph
import tss.t.shared.model.TSDataState
import tss.t.shared.model.TrendingPodcastRes
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class SafeApiCallTest {

    @Test
    fun cancellationThrownByTheRequestPropagates() = runTest {
        assertFailsWith<CancellationException> {
            safeApiCall<TrendingPodcastRes>(PodcastIndexJson, TrendingPodcastRes.serializer()) {
                throw CancellationException("cancelled")
            }
        }
    }

    @Test
    fun otherFailuresBecomeError() = runTest {
        val boom = IllegalArgumentException("boom")

        val result = safeApiCall<TrendingPodcastRes>(PodcastIndexJson, TrendingPodcastRes.serializer()) {
            throw boom
        }

        assertSame(boom, assertIs<TSDataState.Error<*>>(result).exception)
    }

    @Test
    fun cancellingTheCallerWhileTheRequestIsInFlightPropagates() = runTest {
        val started = CompletableDeferred<Unit>()
        val mock = MockApi(handler = {
            started.complete(Unit)
            awaitCancellation()
        })
        var outcome: Any? = null

        val job = launch {
            outcome = try {
                mock.api.getTrending(since = 1L)
            } catch (e: CancellationException) {
                outcome = e
                throw e
            }
        }
        started.await()
        job.cancel()
        job.join()

        assertIs<CancellationException>(outcome, "got $outcome instead of a CancellationException")
    }

    @Test
    fun cancellingARetryingUseCaseNeitherRetriesNorReturnsError() = runTest {
        val started = CompletableDeferred<Unit>()
        val test = TestGraph(handler = {
            started.complete(Unit)
            awaitCancellation()
        })
        var outcome: Any? = null

        val job = launch {
            outcome = try {
                test.graph.getEpisodeByFeedId("42")
            } catch (e: CancellationException) {
                outcome = e
                throw e
            }
        }
        started.await()
        job.cancel()
        job.join()

        assertIs<CancellationException>(outcome, "got $outcome instead of a CancellationException")
        assertEquals(1, test.requests.size)
    }
}
