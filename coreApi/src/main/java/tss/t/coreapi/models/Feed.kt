package tss.t.coreapi.models

data class Feed(
    val artwork: String,
    val author: String,
    val categories: Categories,
    val contentType: String,
    val crawlErrors: Int,
    val dead: Int,
    val description: String,
    val episodeCount: Int,
    val explicit: Boolean,
    val generator: String,
    val id: Int,
    val image: String,
    val imageUrlHash: Long,
    val itunesId: Int?,
    val language: String,
    val lastCrawlTime: Int,
    val lastGoodHttpStatusTime: Int,
    val lastHttpStatus: Int,
    val lastParseTime: Int,
    val lastUpdateTime: Int,
    val link: String,
    val locked: Int,
    val medium: String,
    val newestItemPubdate: Int,
    val originalUrl: String,
    val ownerName: String,
    val parseErrors: Int,
    val podcastGuid: String,
    val title: String,
    val type: Int,
    val url: String,
    val chash: String? = null,
    val funding: Funding? = null,
    val itunesType: String? = null,
    val value: Value? = null
)


data class Model(
    val method: String,
    val suggested: String? = null,
    val type: String
)
