package tss.t.core.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ApiErrorAnalyticsInterceptorTest {

    @Test
    fun endpointDropsTheApiVersionAndIds() {
        assertEquals("podcasts/byfeedid", ApiErrorAnalyticsInterceptor.endpointOf("/api/1.0/podcasts/byfeedid"))
        assertEquals("search/music/byterm", ApiErrorAnalyticsInterceptor.endpointOf("/api/1.0/search/music/byterm/"))
        assertEquals("feeds/:id/episodes", ApiErrorAnalyticsInterceptor.endpointOf("/feeds/920666/episodes"))
        assertEquals("/", ApiErrorAnalyticsInterceptor.endpointOf("/"))
    }

    @Test
    fun errorTypes() {
        assertEquals("network", ApiErrorAnalyticsInterceptor.errorType(UnknownHostException()))
        assertEquals("timeout", ApiErrorAnalyticsInterceptor.errorType(SocketTimeoutException()))
        assertEquals("timeout", ApiErrorAnalyticsInterceptor.errorType(InterruptedIOException()))
        assertEquals("other", ApiErrorAnalyticsInterceptor.errorType(java.io.IOException("x")))
    }
}
