package tss.t.coreapi.models

import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import kotlinx.parcelize.Parcelize

@Parcelize
data class Feed(
    val artwork: String,
    val author: String,
    val categories: Categories?,
    val contentType: String,
    val crawlErrors: Int,
    val dead: Int,
    val description: String,
    val episodeCount: Int,
    val explicit: Boolean,
    val generator: String,
    val id: Long,
    val image: String,
    val imageUrlHash: Long,
    val itunesId: Long?,
    val language: String,
    val lastCrawlTime: Long,
    val lastGoodHttpStatusTime: Int,
    val lastHttpStatus: Int,
    val lastParseTime: Long,
    val lastUpdateTime: Long,
    val link: String,
    val locked: Int,
    val medium: String,
    val newestItemPubdate: Long,
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
) : Parcelable {
    companion object {
        @VisibleForTesting
        val testItem by lazy {
            Feed(
                artwork = "",
                author = "Test",
                categories = Categories(),
                contentType = "",
                crawlErrors = 1,
                dead = 1,
                description = "Test description",
                newestItemPubdate = 0,
                lastHttpStatus = 1,
                lastUpdateTime = 1,
                generator = "",
                ownerName = "",
                link = "",
                type = 1,
                episodeCount = 1,
                imageUrlHash = 1L,
                lastCrawlTime = 1L,
                lastParseTime = 1L,
                itunesType = "",
                originalUrl = "",
                parseErrors = 1,
                podcastGuid = "",
                id = 1,
                url = "",
                chash = "",
                image = "",
                title = "Title search",
                value = null,
                locked = 1,
                medium = "",
                explicit = true,
                itunesId = 0,
                language = "vi",
                lastGoodHttpStatusTime = 0,
                funding = null
            )
        }
    }
}

@Parcelize
data class Model(
    val method: String,
    val suggested: String? = null,
    val type: String
) : Parcelable
