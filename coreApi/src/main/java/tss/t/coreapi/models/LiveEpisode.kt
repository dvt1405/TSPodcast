package tss.t.coreapi.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Calendar

@Parcelize
data class LiveEpisode(
    //@SerializedName("categories")
    val categories: Categories? = null,
    //@SerializedName("chaptersUrl")
    val chaptersUrl: String?,
    //@SerializedName("contentLink")
    val contentLink: String,
    //@SerializedName("dateCrawled")
    val dateCrawled: Long,
    //@SerializedName("datePublished")
    val datePublished: Long,
    //@SerializedName("datePublishedPretty")
    val datePublishedPretty: String,
    //@SerializedName("description")
    val description: String?,
    //@SerializedName("duration")
    val duration: Long,
    //@SerializedName("enclosureLength")
    val enclosureLength: Long,
    //@SerializedName("enclosureType")
    val enclosureType: String,
    //@SerializedName("enclosureUrl")
    val enclosureUrl: String,
    //@SerializedName("endTime")
    val endTime: Long,
    //@SerializedName("episode")
    val episode: Long,
    //@SerializedName("episodeType")
    val episodeType: String?,
    //@SerializedName("explicit")
    val explicit: Int,
    //@SerializedName("feedDead")
    val feedDead: Int,
    //@SerializedName("feedDuplicateOf")
    val feedDuplicateOf: Long,
    //@SerializedName("feedId")
    val feedId: Long,
    //@SerializedName("feedImage")
    val feedImage: String,
    //@SerializedName("feedItunesId")
    val feedItunesId: Long,
    //@SerializedName("feedLanguage")
    val feedLanguage: String?,
    //@SerializedName("guid")
    val guid: String,
    //@SerializedName("id")
    val id: Long,
    //@SerializedName("image")
    val image: String,
    //@SerializedName("link")
    val link: String,
    //@SerializedName("season")
    val season: Int,
    //@SerializedName("startTime")
    val startTime: Long,
    //@SerializedName("status")
    val status: String,
    //@SerializedName("title")
    val title: String,
    //@SerializedName("transcriptUrl")
    val transcriptUrl: String
) : Parcelable {
    companion object {
        val default by lazy {
            LiveEpisode(
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
                guid = "1",
                id = 100,
                image = "",
                link = "",
                title = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                description = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Ut tempus, sem vitae convallis imperdiet, lectus nunc pharetra diam, ac rhoncus quam eros eu risus. Nulla pulvinar condimentum erat, pulvinar tempus turpis blandit ut. Etiam sed ipsum sed lacus eleifend hendrerit eu quis quam. Etiam ligula eros, finibus vestibulum tortor ac, ultrices accumsan dolor. Vivamus vel nisl a libero lobortis posuere. Aenean facilisis nibh vel ultrices bibendum. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Suspendisse ac est vitae lacus commodo efficitur at ut massa. Etiam vestibulum sit amet sapien sed varius. Aliquam non ipsum imperdiet, pulvinar enim nec, mollis risus. Fusce id tincidunt nisl.",
                duration = 10L,
                episode = 1,
                chaptersUrl = "",
                episodeType = "",
                feedDead = 1,
                feedDuplicateOf = 1L,
                season = 1,
                transcriptUrl = "",
                categories = null,
                contentLink = "",
                endTime = 1L,
                startTime = 1L,
                status = ""
            )
        }
    }
}