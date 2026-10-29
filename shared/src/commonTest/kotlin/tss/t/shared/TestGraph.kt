package tss.t.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import tss.t.shared.config.ConfigAPI
import tss.t.shared.config.ConfigKeys
import tss.t.shared.network.CredentialsProvider
import tss.t.shared.network.FakeCredentials
import tss.t.shared.network.FixedClock
import tss.t.shared.network.respondJson
import kotlin.time.Duration

/** Remote Config stand-in; [values] can change between `reload()` calls. */
class FakeConfig(var values: Map<String, String?> = emptyMap()) : ConfigAPI {
    override fun getString(key: String): String? = values[key]

    companion object {
        fun blacklist(json: String?) = FakeConfig(mapOf(ConfigKeys.KEY_BLACK_LIST to json))
    }
}

/** A real [SharedGraph] on a [MockEngine] and a fake clock; records every request. */
class TestGraph(
    config: ConfigAPI = FakeConfig(),
    credentials: CredentialsProvider = FakeCredentials(),
    val clock: FixedClock = FixedClock(START_MILLIS),
    private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respondJson("{}")
    },
) {
    val requests = mutableListOf<HttpRequestData>()

    val engine = MockEngine { request ->
        requests += request
        handler(request)
    }

    val graph = SharedGraph(credentials, config, engine, clock)

    /** Requests whose path ends with [path], e.g. `"podcasts/trending"`. */
    fun requestsTo(path: String): Int = requests.count { it.url.encodedPath.endsWith("/$path") }

    fun advance(by: Duration) {
        clock.millis += by.inWholeMilliseconds
    }

    companion object {
        const val START_MILLIS = 1_700_000_000_000L
    }
}
