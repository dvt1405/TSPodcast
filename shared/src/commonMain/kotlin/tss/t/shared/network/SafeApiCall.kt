package tss.t.shared.network

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import tss.t.shared.model.TSDataState
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs one request and wraps the outcome, replacing Android's `TSDataStateCallAdapterFactory`:
 * - 2xx with a body that parses: `Success`;
 * - non-2xx: `Error(PodcastIndexHttpException(code, body))`;
 * - IO, timeout or parse failure: `Error(cause)`.
 *
 * `CancellationException` is rethrown so structured concurrency keeps working.
 */
suspend fun <T : Any> safeApiCall(
    json: Json,
    deserializer: DeserializationStrategy<T>,
    request: suspend () -> HttpResponse,
): TSDataState<T> = try {
    val response = request()
    val body = response.bodyAsText()
    if (response.status.isSuccess()) {
        TSDataState.Success(json.decodeFromString(deserializer, body))
    } else {
        TSDataState.Error(PodcastIndexHttpException(response.status.value, body))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    TSDataState.Error(e)
}
