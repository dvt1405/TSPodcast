package tss.t.shared.model

import kotlinx.serialization.Serializable

/**
 * A podcast as returned by `podcasts/trending` and `recent/feeds`
 * (Android: `tss.t.coreapi.models.Podcast`).
 *
 * Those endpoints only send `id, url, title, description, author, image, artwork,
 * newestItemPublishTime, itunesId, trendScore, language, categories`, so every other
 * field is optional. `feedId` is null for them; [fromFeed] fills it.
 *
 * `explicit` is an Int (0/1) here, unlike [Feed.explicit] which is a Boolean.
 */
@Serializable
data class Podcast(
    @Serializable(with = CategoriesSerializer::class)
    val categories: Categories? = null,
    val dateCrawled: Long = 0L,
    val datePublished: Long = 0L,
    val datePublishedPretty: String? = null,
    val enclosureLength: Long = 0L,
    val enclosureType: String? = null,
    val enclosureUrl: String? = null,
    val explicit: Int = 0,
    val feedId: Long? = null,
    val feedImage: String? = null,
    val feedItunesId: Long? = null,
    val feedLanguage: String? = null,
    val feedTitle: String? = null,
    val guid: String? = null,
    val id: Long = 0L,
    val image: String = "",
    val link: String? = null,
    val title: String? = null,
    val description: String? = null,
    // Sent by trending / recent feeds; Android's Podcast drops them but iOS lists show the author.
    val author: String? = null,
    val artwork: String? = null,
    val url: String? = null,
    val language: String? = null,
    val itunesId: Long? = null,
    val trendScore: Int? = null,
    val newestItemPublishTime: Long? = null,
) {
    companion object {
        /** Same mapping as Android `Podcast.fromFeed`, used for feed-shaped responses (E6). */
        fun fromFeed(feed: Feed): Podcast = Podcast(
            categories = feed.categories,
            dateCrawled = feed.lastCrawlTime,
            datePublished = feed.newestItemPubdate,
            datePublishedPretty = "",
            enclosureLength = 100,
            enclosureType = null,
            enclosureUrl = null,
            explicit = if (feed.explicit) 1 else 0,
            feedId = feed.id,
            feedImage = feed.image,
            feedItunesId = feed.itunesId,
            feedLanguage = feed.language,
            feedTitle = feed.title,
            guid = feed.podcastGuid,
            id = feed.id,
            image = feed.artwork,
            link = feed.link,
            title = feed.title,
            description = feed.description,
            author = feed.author,
            artwork = feed.artwork,
            url = feed.url,
            language = feed.language,
            itunesId = feed.itunesId,
            newestItemPublishTime = feed.newestItemPubdate,
        )
    }
}
