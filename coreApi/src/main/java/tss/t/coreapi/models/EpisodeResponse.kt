package tss.t.coreapi.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Calendar

data class EpisodeResponse(
    val count: Int,
    val items: List<Episode>,
    val liveItems: List<LiveEpisode>? = null,
    val query: String? = null,
) : BaseResponse() {
    @Expose(serialize = false, deserialize = false)
    var type: Type? = null

    enum class Type {
        RecentEpisode,
        LiveEpisode
    }
}

@Parcelize
@Entity
data class Episode(
    val chaptersUrl: String?,
    val dateCrawled: Long,
    val datePublished: Long,
    val datePublishedPretty: String,
    val description: String?,
    val duration: Long,
    val enclosureLength: Long,
    val enclosureType: String,
    @SerializedName("enclosureUrl")
    val enclosureUrl: String,
    val episode: Long?,
    val episodeType: String?,
    val explicit: Int,
    val feedDead: Int,
    val feedDuplicateOf: Long?,
    val feedId: Long,
    val feedImage: String,
    val feedItunesId: Long,
    val feedLanguage: String?,
    val feedUrl: String,
    val guid: String,
    @PrimaryKey
    val id: Long,
    val image: String,
    val link: String,
    val persons: List<Person>? = null,
    val podcastGuid: String,
    val season: Int,
    val socialInteract: List<SocialInteract>?,
    val soundbite: Soundbite?,
    val soundbites: List<Soundbite>?,
    val title: String,
    val transcriptUrl: String?,
    val transcripts: List<PodcastTranscript>?,
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
    val categories: Categories? = null,
    val chaptersUrl: String?,
    val contentLink: String,
    val dateCrawled: Long,
    val datePublished: Long,
    val datePublishedPretty: String,
    val description: String?,
    val duration: Long,
    val enclosureLength: Long,
    val enclosureType: String,
    @SerializedName("enclosureUrl")
    val enclosureUrl: String,
    val endTime: Long,
    val episode: Long,
    val episodeType: String?,
    val explicit: Int,
    val feedDead: Int,
    val feedDuplicateOf: Long,
    val feedId: Long,
    val feedImage: String,
    val feedItunesId: Long,
    val feedLanguage: String?,
    val guid: String,
    val id: Long,
    @SerializedName("image")
    val image: String,
    val link: String,
    val season: Int,
    val startTime: Long,
    val status: String,
    val title: String,
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
    val accountId: String,
    val accountUrl: String,
    val priority: Int,
    val protocol: String,
    val url: String
) : Parcelable