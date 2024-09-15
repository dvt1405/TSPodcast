package tss.t.coreapi.models

import android.annotation.SuppressLint
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Calendar

data class TrendingPodcastRes(
    val count: Int,
    @SerializedName("feeds")
    val items: List<TrendingPodcast>,
    val max: Int?
) : BaseResponse()

data class TrendingPodcast(
    val categories: Categories? = Categories(),
    val dateCrawled: Int,
    val datePublished: Int,
    val datePublishedPretty: String?,
    val enclosureLength: Int,
    val enclosureType: String?,
    val enclosureUrl: String?,
    val explicit: Int,
    val feedId: Int,
    val feedImage: String?,
    val feedItunesId: Int?,
    val feedLanguage: String?,
    val feedTitle: String?,
    val guid: String?,
    val id: Int,
    val image: String,
    val link: String?,
    val title: String
) {
    @SuppressLint("SimpleDateFormat")
    companion object {
        val default by lazy {
            TrendingPodcast(
                categories = Categories(),
                dateCrawled = System.currentTimeMillis().toInt(),
                datePublished = System.currentTimeMillis().toInt(),
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
                title = "Test"
            )
        }
    }
}