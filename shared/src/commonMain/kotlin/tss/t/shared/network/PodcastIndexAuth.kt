package tss.t.shared.network

import okio.ByteString.Companion.encodeUtf8
import tss.t.shared.util.EpochClock

/**
 * Signs PodcastIndex requests exactly like Android's `NetworkModule.provideClient`
 * interceptor with the `securedToken` native values.
 *
 * Android's C++ reads the clock twice (once for `X-Auth-Date`, once inside the hash), which
 * can disagree across a second boundary. Here one timestamp feeds both headers.
 */
class PodcastIndexAuth(
    private val credentials: CredentialsProvider,
    private val clock: EpochClock = EpochClock.System,
) {
    val baseUrl: String get() = credentials.baseUrl

    /** The 4 PodcastIndex headers, computed from a single timestamp in epoch seconds. */
    fun headers(): Map<String, String> {
        val date = (clock.nowMillis() / 1_000L).toString()
        val apiKey = credentials.apiKey
        return mapOf(
            HEADER_DATE to date,
            HEADER_KEY to apiKey,
            HEADER_AUTHORIZATION to authorization(apiKey, credentials.apiSecret, date),
            HEADER_USER_AGENT to credentials.userAgent,
        )
    }

    companion object {
        const val HEADER_DATE = "X-Auth-Date"
        const val HEADER_KEY = "X-Auth-Key"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_USER_AGENT = "User-Agent"

        /** `sha1(apiKey + apiSecret + date)` as lowercase hex. */
        fun authorization(apiKey: String, apiSecret: String, date: String): String =
            (apiKey + apiSecret + date).encodeUtf8().sha1().hex()
    }
}
