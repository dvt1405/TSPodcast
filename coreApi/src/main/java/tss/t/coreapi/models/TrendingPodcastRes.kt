package tss.t.coreapi.models

import android.annotation.SuppressLint
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Calendar

@Parcelize
data class TrendingPodcastRes(
    @SerializedName("count")
    val count: Int,
    @SerializedName("feeds")
    val items: List<Podcast>,
    @SerializedName("max")
    val max: Int?
) : BaseResponse(), Parcelable {

    @IgnoredOnParcel
    @Expose(deserialize = false, serialize = false)
    var type: Type? = Type.Trending

    enum class Type {
        Trending,
        RecentFeed,
        RecentNewFeed
    }
}

@Parcelize
@Entity
data class Podcast(
    @SerializedName("categories")
    @ColumnInfo("categories")
    val categories: Categories? = Categories(),
    @SerializedName("dateCrawled")
    @ColumnInfo("dateCrawled")
    val dateCrawled: Long,
    @SerializedName("datePublished")
    @ColumnInfo("datePublished")
    val datePublished: Long,
    @SerializedName("datePublishedPretty")
    @ColumnInfo("datePublishedPretty")
    val datePublishedPretty: String?,
    @SerializedName("enclosureLength")
    @ColumnInfo("enclosureLength")
    val enclosureLength: Long,
    @SerializedName("enclosureType")
    @ColumnInfo("enclosureType")
    val enclosureType: String?,
    @SerializedName("enclosureUrl")
    @ColumnInfo("enclosureUrl")
    val enclosureUrl: String?,
    @SerializedName("explicit")
    @ColumnInfo("explicit")
    val explicit: Int,
    @SerializedName("feedId")
    @ColumnInfo("feedId")
    val feedId: Long,
    @SerializedName("feedImage")
    @ColumnInfo("feedImage")
    val feedImage: String?,
    @SerializedName("feedItunesId")
    @ColumnInfo("feedItunesId")
    val feedItunesId: Long?,
    @SerializedName("feedLanguage")
    @ColumnInfo("feedLanguage")
    val feedLanguage: String?,
    @SerializedName("feedTitle")
    @ColumnInfo("feedTitle")
    val feedTitle: String?,
    @SerializedName("guid")
    @ColumnInfo("guid")
    val guid: String?,
    @PrimaryKey
    @ColumnInfo("id")
    @SerializedName("id")
    val id: Long,
    @SerializedName("image")
    @ColumnInfo("image")
    val image: String,
    @SerializedName("link")
    @ColumnInfo("link")
    val link: String?,
    @SerializedName("title")
    @ColumnInfo("title")
    val title: String?,
    @SerializedName("description")
    @ColumnInfo("description")
    val description: String?
) : Parcelable {
    @SuppressLint("SimpleDateFormat")
    companion object {
        fun fromFeed(feed: Feed): Podcast {
            return Podcast(
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
                description = feed.description
            )
        }

        val default by lazy {
            Podcast(
                categories = Categories(),
                dateCrawled = System.currentTimeMillis(),
                datePublished = System.currentTimeMillis(),
                datePublishedPretty = SimpleDateFormat("yyyy, mm, dd - hh:mm")
                    .format(Calendar.getInstance().time),
                enclosureLength = 100,
                enclosureType = "Test",
                enclosureUrl = "Test",
                explicit = 1,
                feedId = 1,
                feedImage = "",
                feedItunesId = 1,
                feedLanguage = "vi",
                feedTitle = "Test",
                guid = "1",
                id = 100,
                image = "",
                link = "",
                title = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                description = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Ut tempus, sem vitae convallis imperdiet, lectus nunc pharetra diam, ac rhoncus quam eros eu risus. Nulla pulvinar condimentum erat, pulvinar tempus turpis blandit ut. Etiam sed ipsum sed lacus eleifend hendrerit eu quis quam. Etiam ligula eros, finibus vestibulum tortor ac, ultrices accumsan dolor. Vivamus vel nisl a libero lobortis posuere. Aenean facilisis nibh vel ultrices bibendum. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Suspendisse ac est vitae lacus commodo efficitur at ut massa. Etiam vestibulum sit amet sapien sed varius. Aliquam non ipsum imperdiet, pulvinar enim nec, mollis risus. Fusce id tincidunt nisl."
            )
        }
    }
}