package tss.t.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The one Json configuration for PodcastIndex payloads: unknown keys ignored, nulls and
 * missing keys fall back to the property default, lenient about quoted/unquoted literals
 * (the API sends `"max": "100"` on some endpoints).
 */
val PodcastIndexJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

/** Platform engine: OkHttp on Android, Darwin (URLSession) on iOS. */
expect fun defaultHttpClientEngine(): HttpClientEngine

/**
 * HttpClient for PodcastIndex. Every request gets the 4 auth headers from [auth] and is
 * resolved against `auth.baseUrl`.
 *
 * There is deliberately no global `HttpRequestRetry`: Android only retries E10/E11, and that
 * policy lives with those use cases.
 */
fun createHttpClient(
    engine: HttpClientEngine,
    auth: PodcastIndexAuth,
    json: Json = PodcastIndexJson,
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
    // Evaluated per request, so the base URL is always the provider's current value.
    defaultRequest { url(auth.baseUrl.withTrailingSlash()) }
    install(
        createClientPlugin("PodcastIndexAuth") {
            onRequest { request, _ ->
                auth.headers().forEach { (name, value) -> request.headers[name] = value }
            }
        }
    )
}

private const val REQUEST_TIMEOUT_MS = 20_000L

/** Paths are relative ("podcasts/trending"), so the base must end with `/`, as in Retrofit. */
private fun String.withTrailingSlash(): String = if (endsWith("/")) this else "$this/"
