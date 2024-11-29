package tss.t.coreapi.models

import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity
data class Feed(
    @SerializedName("artwork")
    @ColumnInfo("artwork")
    val artwork: String,
    @SerializedName("author")
    @ColumnInfo("author")
    val author: String,
    @SerializedName("categories")
    @ColumnInfo("categories")
    val categories: Categories?,
    @SerializedName("contentType")
    @ColumnInfo("contentType")
    val contentType: String,
    @SerializedName("crawlErrors")
    @ColumnInfo("crawlErrors")
    val crawlErrors: Int,
    @SerializedName("dead")
    @ColumnInfo("dead")
    val dead: Int,
    @SerializedName("description")
    @ColumnInfo("description")
    val description: String,
    @SerializedName("episodeCount")
    @ColumnInfo("episodeCount")
    val episodeCount: Int,
    @SerializedName("explicit")
    @ColumnInfo("explicit")
    val explicit: Boolean,
    @SerializedName("generator")
    @ColumnInfo("generator")
    val generator: String,
    @SerializedName("id")
    @ColumnInfo("id")
    @PrimaryKey
    val id: Long,
    @SerializedName("image")
    @ColumnInfo("image")
    val image: String,
    @SerializedName("imageUrlHash")
    @ColumnInfo("imageUrlHash")
    val imageUrlHash: Long,
    @SerializedName("itunesId")
    @ColumnInfo("itunesId")
    val itunesId: Long?,
    @SerializedName("language")
    @ColumnInfo("language")
    val language: String,
    @SerializedName("lastCrawlTime")
    @ColumnInfo("lastCrawlTime")
    val lastCrawlTime: Long,
    @SerializedName("lastGoodHttpStatusTime")
    @ColumnInfo("lastGoodHttpStatusTime")
    val lastGoodHttpStatusTime: Int,
    @SerializedName("lastHttpStatus")
    @ColumnInfo("lastHttpStatus")
    val lastHttpStatus: Int,
    @SerializedName("lastParseTime")
    @ColumnInfo("lastParseTime")
    val lastParseTime: Long,
    @SerializedName("lastUpdateTime")
    @ColumnInfo("lastUpdateTime")
    val lastUpdateTime: Long,
    @SerializedName("link")
    @ColumnInfo("link")
    val link: String,
    @SerializedName("locked")
    @ColumnInfo("locked")
    val locked: Int,
    @SerializedName("medium")
    @ColumnInfo("medium")
    val medium: String,
    @SerializedName("newestItemPubdate")
    @ColumnInfo("newestItemPubdate")
    val newestItemPubdate: Long,
    @SerializedName("originalUrl")
    @ColumnInfo("originalUrl")
    val originalUrl: String,
    @SerializedName("ownerName")
    @ColumnInfo("ownerName")
    val ownerName: String,
    @SerializedName("parseErrors")
    @ColumnInfo("parseErrors")
    val parseErrors: Int,
    @SerializedName("podcastGuid")
    @ColumnInfo("podcastGuid")
    val podcastGuid: String,
    @SerializedName("title")
    @ColumnInfo("title")
    val title: String,
    @SerializedName("type")
    @ColumnInfo("type")
    val type: Int,
    @SerializedName("url")
    @ColumnInfo("url")
    val url: String,
    @SerializedName("chash")
    @ColumnInfo("chash")
    val chash: String? = null,
    @SerializedName("funding")
    @ColumnInfo("funding")
    val funding: Funding? = null,
    @SerializedName("itunesType")
    @ColumnInfo("itunesType")
    val itunesType: String? = null,
    @SerializedName("value")
    @ColumnInfo("value")
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
    @SerializedName("method")
    val method: String,
    @SerializedName("suggested")
    val suggested: String? = null,
    @SerializedName("type")
    val type: String
) : Parcelable
