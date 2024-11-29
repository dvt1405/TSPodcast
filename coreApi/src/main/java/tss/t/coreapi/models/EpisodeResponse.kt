package tss.t.coreapi.models

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Calendar

data class EpisodeResponse(
    @SerializedName("count")
    val count: Int,
    @SerializedName("items")
    val items: List<Episode>,
    @SerializedName("liveItems")
    val liveItems: List<LiveEpisode>? = null,
    @SerializedName("query")
    val query: String? = null,
) : BaseResponse() {
    @Expose(serialize = false, deserialize = false)
    var type: Type? = null

    @Keep
    enum class Type {
        RecentEpisode,
        LiveEpisode
    }
}

@Parcelize
@Entity
data class Episode(
    @SerializedName("chaptersUrl")
    val chaptersUrl: String?,
    @SerializedName("dateCrawled")
    val dateCrawled: Long,
    @SerializedName("datePublished")
    val datePublished: Long,
    @SerializedName("datePublishedPretty")
    val datePublishedPretty: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("duration")
    val duration: Long,
    @SerializedName("enclosureLength")
    val enclosureLength: Long,
    @SerializedName("enclosureType")
    val enclosureType: String,
    @SerializedName("enclosureUrl")
    val enclosureUrl: String,
    @SerializedName("episode")
    val episode: Long?,
    @SerializedName("episodeType")
    val episodeType: String?,
    @SerializedName("explicit")
    val explicit: Int,
    @SerializedName("feedDead")
    val feedDead: Int,
    @SerializedName("feedDuplicateOf")
    val feedDuplicateOf: Long?,
    @SerializedName("feedId")
    val feedId: Long,
    @SerializedName("feedImage")
    val feedImage: String,
    @SerializedName("feedItunesId")
    val feedItunesId: Long,
    @SerializedName("feedLanguage")
    val feedLanguage: String?,
    @SerializedName("feedUrl")
    val feedUrl: String,
    @SerializedName("guid")
    val guid: String,
    @PrimaryKey
    @SerializedName("id")
    val id: Long,
    @SerializedName("image")
    val image: String,
    @SerializedName("link")
    val link: String,
    @SerializedName("persons")
    val persons: List<Person>? = null,
    @SerializedName("podcastGuid")
    val podcastGuid: String,
    @SerializedName("season")
    val season: Int,
    @SerializedName("socialInteract")
    val socialInteract: List<SocialInteract>?,
    @SerializedName("soundbite")
    val soundbite: Soundbite?,
    @SerializedName("soundbites")
    val soundbites: List<Soundbite>?,
    @SerializedName("title")
    val title: String,
    @SerializedName("transcriptUrl")
    val transcriptUrl: String?,
    @SerializedName("transcripts")
    val transcripts: List<PodcastTranscript>?,
    @SerializedName("value")
    val value: Value?
) : Parcelable {
    companion object {
        val default by lazy {
            Episode(
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
                feedUrl = "",
                persons = listOf(),
                podcastGuid = "",
                season = 1,
                socialInteract = emptyList(),
                soundbite = null,
                soundbites = emptyList(),
                transcripts = emptyList(),
                transcriptUrl = "",
                value = null
            )
        }

        fun fromLive(liveEpisode: LiveEpisode) = Episode(
            dateCrawled = liveEpisode.dateCrawled,
            datePublished = liveEpisode.datePublished,
            datePublishedPretty = liveEpisode.datePublishedPretty,
            enclosureLength = liveEpisode.enclosureLength,
            enclosureType = liveEpisode.enclosureType,
            enclosureUrl = liveEpisode.enclosureUrl,
            explicit = liveEpisode.explicit,
            feedId = liveEpisode.feedId,
            feedImage = liveEpisode.feedImage,
            feedItunesId = liveEpisode.feedItunesId,
            feedLanguage = liveEpisode.feedLanguage,
            guid = liveEpisode.guid,
            id = liveEpisode.id,
            image = liveEpisode.feedImage,
            link = liveEpisode.link,
            title = liveEpisode.title,
            description = liveEpisode.description,
            duration = liveEpisode.duration,
            episode = liveEpisode.episode,
            chaptersUrl = liveEpisode.chaptersUrl,
            episodeType = liveEpisode.episodeType,
            feedDead = liveEpisode.feedDead,
            feedDuplicateOf = liveEpisode.feedDuplicateOf,
            feedUrl = liveEpisode.chaptersUrl ?: "",
            persons = listOf(),
            podcastGuid = liveEpisode.guid,
            season = liveEpisode.season,
            socialInteract = emptyList(),
            soundbite = null,
            soundbites = emptyList(),
            transcripts = emptyList(),
            transcriptUrl = liveEpisode.transcriptUrl,
            value = null
        )
    }
}

@Parcelize
data class LiveEpisode(
    @SerializedName("categories")
    val categories: Categories? = null,
    @SerializedName("chaptersUrl")
    val chaptersUrl: String?,
    @SerializedName("contentLink")
    val contentLink: String,
    @SerializedName("dateCrawled")
    val dateCrawled: Long,
    @SerializedName("datePublished")
    val datePublished: Long,
    @SerializedName("datePublishedPretty")
    val datePublishedPretty: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("duration")
    val duration: Long,
    @SerializedName("enclosureLength")
    val enclosureLength: Long,
    @SerializedName("enclosureType")
    val enclosureType: String,
    @SerializedName("enclosureUrl")
    val enclosureUrl: String,
    @SerializedName("endTime")
    val endTime: Long,
    @SerializedName("episode")
    val episode: Long,
    @SerializedName("episodeType")
    val episodeType: String?,
    @SerializedName("explicit")
    val explicit: Int,
    @SerializedName("feedDead")
    val feedDead: Int,
    @SerializedName("feedDuplicateOf")
    val feedDuplicateOf: Long,
    @SerializedName("feedId")
    val feedId: Long,
    @SerializedName("feedImage")
    val feedImage: String,
    @SerializedName("feedItunesId")
    val feedItunesId: Long,
    @SerializedName("feedLanguage")
    val feedLanguage: String?,
    @SerializedName("guid")
    val guid: String,
    @SerializedName("id")
    val id: Long,
    @SerializedName("image")
    val image: String,
    @SerializedName("link")
    val link: String,
    @SerializedName("season")
    val season: Int,
    @SerializedName("startTime")
    val startTime: Long,
    @SerializedName("status")
    val status: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("transcriptUrl")
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

@Parcelize
data class SocialInteract(
    @SerializedName("accountId")
    val accountId: String,
    @SerializedName("accountUrl")
    val accountUrl: String,
    @SerializedName("priority")
    val priority: Int,
    @SerializedName("protocol")
    val protocol: String,
    @SerializedName("url")
    val url: String
) : Parcelable