package tss.t.shared.usecase

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import tss.t.shared.TestGraph
import tss.t.shared.model.CategoryRes
import tss.t.shared.model.TSDataState
import tss.t.shared.network.FakeCredentials
import tss.t.shared.network.respondJson
import tss.t.shared.preferences.AppPreferences
import tss.t.shared.preferences.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

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

    // M1-AC4: disk tier (`CategoryRes`), like PodcastsRepositoryImpl.getCategory.

    private val three = """{"count":3,"feeds":[{"id":1,"name":"Arts"},{"id":2,"name":"Books"},{"id":3,"name":"Design"}]}"""

    @Test
    fun networkSuccessIsPersisted() = runTest {
        val test = TestGraph(handler = { respondJson(three) })

        test.graph.getCategories()

        val raw = assertNotNull(test.store.getString(AppPreferences.KEY_CATEGORY_RES))
        assertEquals(listOf("Arts", "Books", "Design"), test.graph.appPreferences.categoryRes()?.feeds?.map { it.name })
        assertEquals(true, raw.contains("Design"))
    }

    @Test
    fun aNewGraphOnTheSameStoreFallsBackToThePersistedList() = runTest {
        val store = InMemoryKeyValueStore()
        TestGraph(store = store, handler = { respondJson(three) }).graph.getCategories()

        val relaunched = TestGraph(store = store, handler = { respondJson("{}", HttpStatusCode.InternalServerError) })
        val result = relaunched.graph.getCategories().data()

        assertEquals(3, result.count)
        assertEquals(listOf(1, 2, 3), result.feeds.map { it.id })
        assertEquals(1, relaunched.requests.size)
    }

    @Test
    fun emptyStoreAndServerErrorFallBackToTheAsset() = runTest {
        val test = TestGraph(store = InMemoryKeyValueStore(), handler = {
            respondJson("{}", HttpStatusCode.InternalServerError)
        })

        assertEquals(112, test.graph.getCategories().data().feeds.size)
        assertEquals(null, test.store.getString(AppPreferences.KEY_CATEGORY_RES), "the asset is never persisted")
    }

    @Test
    fun missingCredentialsReturnThePersistedListWithoutRequest() = runTest {
        val store = InMemoryKeyValueStore()
        TestGraph(store = store, handler = { respondJson(three) }).graph.getCategories()

        val noCredentials = TestGraph(store = store, credentials = FakeCredentials(apiKey = ""))
        val result = noCredentials.graph.getCategories().data()

        assertEquals(listOf("Arts", "Books", "Design"), result.feeds.map { it.name })
        assertEquals(0, noCredentials.requests.size)
    }

    @Test
    fun corruptPersistedListFallsBackToTheAsset() = runTest {
        val store = InMemoryKeyValueStore()
        store.putString(AppPreferences.KEY_CATEGORY_RES, "{broken")
        val test = TestGraph(store = store, handler = { respondJson("{}", HttpStatusCode.InternalServerError) })

        assertEquals(112, test.graph.getCategories().data().count)
    }
}
