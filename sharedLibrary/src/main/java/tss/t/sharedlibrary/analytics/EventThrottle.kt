package tss.t.sharedlibrary.analytics

/**
 * Keeps noisy events (API and playback errors) from flooding GA4: each key passes at most
 * [perKey] times and all keys together at most [total] times per process. A flaky network
 * can fail every request on every screen; one `api_error` per endpoint and code is enough to
 * see it in the reports.
 */
class EventThrottle(
    private val perKey: Int = 1,
    private val total: Int = 30,
) {
    private val counts = HashMap<String, Int>()
    private var sent = 0

    /** True when an event for [key] may be sent now (and counts it). */
    @Synchronized
    fun allow(key: String): Boolean {
        if (sent >= total) return false
        val n = counts[key] ?: 0
        if (n >= perKey) return false
        counts[key] = n + 1
        sent++
        return true
    }
}
