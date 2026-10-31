package tss.t.shared.usecase

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import tss.t.shared.TestGraph
import tss.t.shared.model.Episode
import tss.t.shared.model.TSDataState
import tss.t.shared.network.PodcastIndexHttpException
import tss.t.shared.network.respondJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** M4-AC10: E12 through the graph, one request, unknown id as an error. */
class GetEpisodeByIdTest {

    @Test
    fun returnsTheEpisode() = runTest {
        val test = TestGraph { respondJson("""{"episode":{"id":456,"feedId":75075,"title":"Tập 1"}}""") }

        val episode = assertIs<TSDataState.Success<*>>(test.graph.getEpisodeById(456)).data

        assertEquals(456L, (episode as Episode).id)
        assertEquals(1, test.requestsTo("episodes/byid"))
    }

    @Test
    fun unknownIdIsAnError() = runTest {
        val test = TestGraph { respondJson("""{"episode":[]}""") }

        val error = assertIs<TSDataState.Error<*>>(test.graph.getEpisodeById(9))

        assertIs<NoSuchElementException>(error.exception)
    }

    @Test
    fun httpErrorIsNotRetried() = runTest {
        val test = TestGraph { respondJson("{}", HttpStatusCode.InternalServerError) }

        val error = assertIs<TSDataState.Error<*>>(test.graph.getEpisodeById(9))

        assertIs<PodcastIndexHttpException>(error.exception)
        assertEquals(1, test.requestsTo("episodes/byid"))
    }
}
