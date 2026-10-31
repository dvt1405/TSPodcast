package tss.t.shared.model

import kotlinx.serialization.Serializable

/**
 * A feed as returned by `search/byterm`, `search/music/byterm`, `podcasts/bymedium` and
 * `podcasts/byfeedid` (Android: `tss.t.coreapi.models.Feed`).
 *
 * `explicit` is a **Boolean** in feed-shaped responses, while [Podcast], [Episode] and
 * [LiveEpisode] use an Int. Mixing the two up is what broke Gson in Android's `GetMusicFeeds`.
 */
@Serializable
data class Feed(
    val artwork: String = "",
    val author: String = "",
    @Serializable(with = CategoriesSerializer::class)
    val categories: Categories? = null,
    val contentType: String = "",
    val crawlErrors: Int = 0,
    val dead: Int = 0,
    val description: String = "",
    val episodeCount: Int = 0,
    val explicit: Boolean = false,
    val generator: String = "",
    val id: Long = 0L,
    val image: String = "",
    val imageUrlHash: Long = 0L,
    val itunesId: Long? = null,
    val language: String = "",
    val lastCrawlTime: Long = 0L,
    val lastGoodHttpStatusTime: Long = 0L,
    val lastHttpStatus: Int = 0,
    val lastParseTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
    val link: String = "",
    val locked: Int = 0,
    val medium: String = "",
    val newestItemPubdate: Long = 0L,
    val originalUrl: String = "",
    val ownerName: String = "",
    val parseErrors: Int = 0,
    val podcastGuid: String = "",
    val title: String = "",
    val type: Int = 0,
    val url: String = "",
    val chash: String? = null,
    val funding: Funding? = null,
    val itunesType: String? = null,
    val value: Value? = null,
)
