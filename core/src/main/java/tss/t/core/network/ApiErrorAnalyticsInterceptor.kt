package tss.t.core.network

import okhttp3.Interceptor
import okhttp3.Response
import tss.t.sharedlibrary.analytics.Analytics
import tss.t.sharedlibrary.analytics.AnalyticsTracker
import tss.t.sharedlibrary.analytics.AppEvents
import tss.t.sharedlibrary.analytics.EventThrottle
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException

/**
 * Logs `api_error` for API calls that fail (HTTP ≥ 400, timeout, no network). Throttled to one
 * event per endpoint + code per process and 30 in all, so an offline session cannot flood
 * GA4. Never changes the request or the response; the exception is rethrown unchanged.
 */
class ApiErrorAnalyticsInterceptor(
    private val tracker: AnalyticsTracker = Analytics,
    private val throttle: EventThrottle = processThrottle,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val endpoint = endpointOf(chain.request().url.encodedPath)
        val response = try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            // A search typed over cancels the previous call: not an error.
            if (!chain.call().isCanceled()) report(endpoint, 0, errorType(e))
            throw e
        }
        if (response.code >= 400) report(endpoint, response.code, "http")
        return response
    }

    private fun report(endpoint: String, code: Int, type: String) {
        if (!throttle.allow("$endpoint/$code/$type")) return
        tracker.log(AppEvents.apiError(endpoint, code, type, tracker.currentScreen))
    }

    companion object {
        /** Shared by every client NetworkModule builds (it is not a singleton). */
        private val processThrottle = EventThrottle(perKey = 1, total = 30)

        /** `/api/1.0/podcasts/byfeedid` → `podcasts/byfeedid`; long numbers become `:id`. */
        fun endpointOf(path: String): String = path
            .removePrefix("/")
            .replace(Regex("""^api/\d+(\.\d+)?/"""), "")
            .split('/')
            .joinToString("/") { if (it.length >= 3 && it.all(Char::isDigit)) ":id" else it }
            .trimEnd('/')
            .ifEmpty { "/" }

        fun errorType(e: IOException): String = when (e) {
            is UnknownHostException -> "network"
            is java.net.ConnectException -> "network"
            is InterruptedIOException -> "timeout"
            else -> "other"
        }
    }
}
