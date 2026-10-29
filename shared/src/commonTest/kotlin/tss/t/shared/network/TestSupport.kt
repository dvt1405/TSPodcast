package tss.t.shared.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import tss.t.shared.util.EpochClock

/** Fake, non-secret credentials for tests. */
data class FakeCredentials(
    override val baseUrl: String = "https://api.example.test/api/1.0/",
    override val apiKey: String = "testkey",
    override val apiSecret: String = "testsecret",
    override val userAgent: String = "TSPodcastTest/1.0",
) : CredentialsProvider

/** Fixed clock; [calls] counts how often it was read. */
class FixedClock(var millis: Long) : EpochClock {
    var calls = 0
        private set

    override fun nowMillis(): Long {
        calls++
        return millis
    }
}

fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(
    content = body,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

/** An API wired to a [MockEngine] that records every request it receives. */
class MockApi(
    credentials: CredentialsProvider = FakeCredentials(),
    clock: EpochClock = FixedClock(1_700_000_000_000L),
    private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respondJson("{}")
    },
) {
    val requests = mutableListOf<HttpRequestData>()

    val engine = MockEngine { request ->
        requests += request
        handler(request)
    }

    val api = PodcastIndexApi(
        client = createHttpClient(engine, PodcastIndexAuth(credentials, clock)),
        credentials = credentials,
    )
}
