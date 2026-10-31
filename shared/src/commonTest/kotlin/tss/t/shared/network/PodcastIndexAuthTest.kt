package tss.t.shared.network

import kotlin.test.Test
import kotlin.test.assertEquals

class PodcastIndexAuthTest {

    private val clock = FixedClock(1_700_000_000_000L)
    private val auth = PodcastIndexAuth(FakeCredentials(), clock)

    @Test
    fun headersAreExactlyTheFourPodcastIndexHeaders() {
        val headers = auth.headers()

        assertEquals(
            mapOf(
                "X-Auth-Date" to "1700000000",
                "X-Auth-Key" to "testkey",
                "Authorization" to "90b6255b079ed6e48084f571b76958438661e91b",
                "User-Agent" to "TSPodcastTest/1.0",
            ),
            headers,
        )
    }

    @Test
    fun clockIsReadOnceSoDateAndHashShareTheTimestamp() {
        auth.headers()

        assertEquals(1, clock.calls)
    }

    @Test
    fun dateIsEpochSecondsTruncated() {
        clock.millis = 1_700_000_000_999L

        assertEquals("1700000000", auth.headers()["X-Auth-Date"])
    }

    @Test
    fun authorizationIsLowercaseSha1OfKeySecretDate() {
        // printf 'testkeytestsecret1700000000' | shasum -a 1
        assertEquals(
            "90b6255b079ed6e48084f571b76958438661e91b",
            PodcastIndexAuth.authorization("testkey", "testsecret", "1700000000"),
        )
    }
}
