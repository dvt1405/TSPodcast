package tss.t.shared.model

import kotlinx.serialization.Serializable

/** An episode of `episodes/byfeedid` (Android: `tss.t.coreapi.models.Episode`). */
@Serializable
data class Episode(
    val chaptersUrl: String? = null,
    val dateCrawled: Long = 0L,
    val datePublished: Long = 0L,
    val datePublishedPretty: String = "",
    val description: String? = null,
    val duration: Long = 0L,
    val enclosureLength: Long = 0L,
    val enclosureType: String = "",
    val enclosureUrl: String = "",
    val episode: Long? = null,
    val episodeType: String? = null,
    val explicit: Int = 0,
    val feedDead: Int = 0,
    val feedDuplicateOf: Long? = null,
    val feedId: Long = 0L,
    val feedImage: String = "",
    val feedItunesId: Long? = null,
    val feedLanguage: String? = null,
    val feedUrl: String = "",
    val guid: String = "",
    val id: Long = 0L,
    val image: String = "",
    val link: String = "",
    val persons: List<Person>? = null,
    val podcastGuid: String = "",
    val season: Int = 0,
    val socialInteract: List<SocialInteract>? = null,
    val soundbite: Soundbite? = null,
    val soundbites: List<Soundbite>? = null,
    val title: String = "",
    val transcriptUrl: String? = null,
    val transcripts: List<PodcastTranscript>? = null,
    val value: Value? = null,
) {
    /** The episode artwork, or the feed artwork when the episode has none (same as Android). */
    fun getImageUrl(): String = image.trim().ifEmpty { feedImage }
}
