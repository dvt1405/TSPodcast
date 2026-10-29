package tss.t.shared.usecase

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import tss.t.shared.TestGraph
import tss.t.shared.model.CategoryRes
import tss.t.shared.model.TSDataState
import tss.t.shared.network.FakeCredentials
import tss.t.shared.network.respondJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetCategoriesTest {

    private val network = """{"count":2,"feeds":[{"id":1,"name":"Arts"},{"id":55,"name":"News"}]}"""

    private fun TSDataState<CategoryRes>.data(): CategoryRes =
        assertIs<TSDataState.Success<CategoryRes>>(this).data

    @Test
    fun serverErrorFallsBackToTheBundledAsset() = runTest {
        val test = TestGraph(handler = { respondJson("{}", HttpStatusCode.InternalServerError) })

        val result = test.graph.getCategories().data()

        assertEquals(112, result.count)
        assertEquals(112, result.feeds.size)
        assertEquals(CategoryRes.Category(id = 1, name = "Arts"), result.feeds.first())
        assertEquals(1, test.requests.size)
    }

    @Test
    fun ioErrorFallsBackToTheBundledAsset() = runTest {
        val test = TestGraph(handler = { throw IllegalStateException("offline") })

        assertEquals(112, test.graph.getCategories().data().count)
    }

    @Test
    fun missingCredentialsFallBackToTheBundledAssetWithoutRequest() = runTest {
        val test = TestGraph(credentials = FakeCredentials(apiSecret = ""))

        assertEquals(112, test.graph.getCategories().data().count)
        assertEquals(0, test.requests.size)
    }

    @Test
    fun networkListIsReturnedAsIs() = runTest {
        val test = TestGraph(handler = { respondJson(network) })

        val result = test.graph.getCategories().data()

        assertEquals(listOf("Arts", "News"), result.feeds.map { it.name })
    }

    @Test
    fun failureAfterASuccessReturnsTheLastNetworkList() = runTest {
        var status = HttpStatusCode.OK
        val test = TestGraph(handler = { respondJson(network, status) })
        test.graph.getCategories()

        status = HttpStatusCode.InternalServerError
        val result = test.graph.getCategories().data()

        assertEquals(2, result.count)
        assertEquals(2, test.requests.size)
    }

    @Test
    fun assetFallbackIsNotKeptAsTheCachedList() = runTest {
        var status = HttpStatusCode.InternalServerError
        val test = TestGraph(handler = { respondJson(network, status) })
        assertEquals(112, test.graph.getCategories(useCache = true).data().count)

        status = HttpStatusCode.OK
        val result = test.graph.getCategories(useCache = true).data()

        assertEquals(2, result.count, "the network is asked again once it works")
        assertEquals(2, test.requests.size)
    }
}
