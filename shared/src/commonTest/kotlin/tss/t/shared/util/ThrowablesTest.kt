package tss.t.shared.util

import tss.t.shared.network.MissingCredentialsException
import tss.t.shared.network.PodcastIndexHttpException
import kotlin.test.Test
import kotlin.test.assertEquals

class ThrowablesTest {
    @Test
    fun typeNameIsTheSimpleKotlinClassName() {
        assertEquals("PodcastIndexHttpException", PodcastIndexHttpException(500, "").typeName)
        assertEquals("MissingCredentialsException", MissingCredentialsException(listOf("apiKey")).typeName)
        assertEquals("IllegalArgumentException", IllegalArgumentException("x").typeName)
    }
}
