package tss.t.shared.deeplink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** M4-AC10: Universal Link paths of the web (seo §A.3) and web `parseIdSlug`. */
class DeepLinkParserTest {

    private val parser = DeepLinkParser(listOf("example.com"))

    @Test
    fun podcastLinks() {
        listOf(
            "https://example.com/podcast/123-ten-podcast",
            "https://example.com/podcast/123",
            "https://example.com/podcast/123/",
            "https://example.com/en/podcast/123-x",
            "https://example.com/podcast/%31%32%33-x",
            "https://example.com/podcast/123-x?utm_source=share#top",
            "HTTPS://EXAMPLE.com/podcast/123-x",
            "https://example.com:443/podcast/123",
            "https://example.com/podcast/123-t%E1%BA%ADp-1",
        ).forEach { url ->
            assertEquals(DeepLink.Podcast(123), parser.parse(url), url)
        }
    }

    @Test
    fun episodeLinks() {
        assertEquals(DeepLink.Episode(456), parser.parse("https://example.com/episode/456-tap-1"))
        assertEquals(DeepLink.Episode(456), parser.parse("https://example.com/en/episode/456"))
        assertEquals(
            DeepLink.Episode(999_999_999_999_999),
            parser.parse("https://example.com/episode/999999999999999-max"),
        )
    }

    @Test
    fun rejectedLinks() {
        listOf(
            "http://example.com/podcast/123",
            "https://other.com/podcast/123",
            "https://example.com.evil.com/podcast/123",
            "https://user@example.com/podcast/123",
            "https://example.com:8443/podcast/123",
            "tss://ts.podcast/playing?mediaId=1",
            "tss://ts.podcast/home",
            "https://example.com/podcast/0",
            "https://example.com/podcast/0-zero",
            "https://example.com/podcast/1234567890123456",
            "https://example.com/podcast/abc-123",
            "https://example.com/podcast/-123",
            "https://example.com/podcast/",
            "https://example.com/podcast",
            "https://example.com/podcast/123/extra",
            "https://example.com/podcast//123",
            "https://example.com/vi/podcast/123",
            "https://example.com/en/en/podcast/123",
            "https://example.com/Podcast/123",
            "https://example.com/category/x",
            "https://example.com/",
            "https://example.com",
            "https://example.com/podcast/%zz-x",
            "https://example.com/podcast/%3",
            "https://example.com/podcast/%E1-x",
            "not a url",
            "",
        ).forEach { url ->
            assertNull(parser.parse(url), url)
        }
    }

    @Test
    fun noAllowedHostMeansNoLink() {
        assertNull(DeepLinkParser(emptyList()).parse("https://example.com/podcast/123"))
        assertNull(DeepLinkParser(listOf("", "  ")).parse("https://example.com/podcast/123"))
    }

    @Test
    fun severalHostsAndCaseInsensitiveConfig() {
        val parser = DeepLinkParser(listOf("Example.com", "www.example.com"))

        assertEquals(DeepLink.Podcast(1), parser.parse("https://example.com/podcast/1"))
        assertEquals(DeepLink.Podcast(1), parser.parse("https://www.example.com/podcast/1"))
    }

    @Test
    fun parseIdSlugMatchesTheWeb() {
        assertEquals(IdSlug(123, "ten-podcast"), DeepLinkParser.parseIdSlug("123-ten-podcast"))
        assertEquals(IdSlug(123, ""), DeepLinkParser.parseIdSlug("123"))
        assertEquals(IdSlug(123, ""), DeepLinkParser.parseIdSlug("123-"))
        assertEquals(IdSlug(7, "a-b-c"), DeepLinkParser.parseIdSlug("007-a-b-c"))
        assertEquals(IdSlug(123, "tập 1"), DeepLinkParser.parseIdSlug("123-t%E1%BA%ADp%201"))
        assertNull(DeepLinkParser.parseIdSlug("0"))
        assertNull(DeepLinkParser.parseIdSlug("1234567890123456"))
        assertNull(DeepLinkParser.parseIdSlug("12a"))
        assertNull(DeepLinkParser.parseIdSlug("١٢٣"), "non-ASCII digits, JavaScript \\d is ASCII")
        assertNull(DeepLinkParser.parseIdSlug(""))
    }
}
