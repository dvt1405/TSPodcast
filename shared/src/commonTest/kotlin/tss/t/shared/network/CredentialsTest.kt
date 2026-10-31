package tss.t.shared.network

import kotlinx.coroutines.test.runTest
import tss.t.shared.model.TSDataState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CredentialsTest {

    @Test
    fun emptyApiKeyReturnsMissingCredentialsWithoutRequest() = runTest {
        assertMissing(FakeCredentials(apiKey = ""), expected = listOf("apiKey"))
    }

    @Test
    fun unexpandedXcconfigPlaceholderReturnsMissingCredentialsWithoutRequest() = runTest {
        assertMissing(
            FakeCredentials(apiKey = "\$(PODCASTINDEX_API_KEY)"),
            expected = listOf("apiKey"),
        )
    }

    @Test
    fun everyBlankValueIsReported() = runTest {
        assertMissing(
            FakeCredentials(baseUrl = " ", apiKey = "", apiSecret = "", userAgent = ""),
            expected = listOf("baseUrl", "apiKey", "apiSecret", "userAgent"),
        )
    }

    @Test
    fun everyEndpointShortCircuits() = runTest {
        val mock = MockApi(credentials = FakeCredentials(apiSecret = "\$(PODCASTINDEX_API_SECRET)"))
        val api = mock.api

        val results = listOf(
            api.getTrending(since = 1L),
            api.getLiveEpisodes(),
            api.getRecentFeeds(cat = "1"),
            api.getPodcastsByMedium(),
            api.searchPodcasts("news"),
            api.searchMusicPodcasts("rock"),
            api.getCategories(),
            api.getEpisodesByFeedId("1"),
            api.getPodcastByFeedId("1"),
        )

        results.forEach { result ->
            assertIs<MissingCredentialsException>(assertIs<TSDataState.Error<*>>(result).exception)
        }
        assertEquals(0, mock.requests.size)
    }

    @Test
    fun completeCredentialsAreNotReported() {
        assertTrue(FakeCredentials().missingCredentials().isEmpty())
    }

    private suspend fun assertMissing(credentials: CredentialsProvider, expected: List<String>) {
        val mock = MockApi(credentials = credentials)

        val result = mock.api.getTrending(since = 1_700_000_000L)

        val error = assertIs<TSDataState.Error<*>>(result)
        val exception = assertIs<MissingCredentialsException>(error.exception)
        assertEquals(expected, exception.missing)
        assertEquals(0, mock.requests.size)
    }
}
