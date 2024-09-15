package tss.t.coreapi.models

data class EpisodeResponse(
    val count: Int,
    val items: List<Episode>,
    val liveItems: List<LiveEpisode>,
    val query: String,
) : BaseResponse()

data class Episode(
    val chaptersUrl: String,
    val dateCrawled: Int,
    val datePublished: Long,
    val datePublishedPretty: String,
    val description: String,
    val duration: Long,
    val enclosureLength: Long,
    val enclosureType: String,
    val enclosureUrl: String,
    val episode: Int,
    val episodeType: String,
    val explicit: Int,
    val feedDead: Int,
    val feedDuplicateOf: Long,
    val feedId: Long,
    val feedImage: String,
    val feedItunesId: Long,
    val feedLanguage: String,
    val feedUrl: String,
    val guid: String,
    val id: Long,
    val image: String,
    val link: String,
    val persons: List<Person>,
    val podcastGuid: String,
    val season: Int,
    val socialInteract: List<SocialInteract>,
    val soundbite: Soundbite,
    val soundbites: List<Soundbite>,
    val title: String,
    val transcriptUrl: String,
    val transcripts: List<PodcastTranscript>,
    val value: Value
)

data class LiveEpisode(
    val chaptersUrl: String,
    val contentLink: String,
    val dateCrawled: Long,
    val datePublished: Long,
    val datePublishedPretty: String,
    val description: String,
    val duration: Long,
    val enclosureLength: Long,
    val enclosureType: String,
    val enclosureUrl: String,
    val endTime: Long,
    val episode: Long,
    val episodeType: String,
    val explicit: Int,
    val feedDead: Int,
    val feedDuplicateOf: Int,
    val feedId: Long,
    val feedImage: String,
    val feedItunesId: Long,
    val feedLanguage: String,
    val guid: String,
    val id: Long,
    val image: String,
    val link: String,
    val season: Int,
    val startTime: Long,
    val status: String,
    val title: String,
    val transcriptUrl: String
)

data class SocialInteract(
    val accountId: String,
    val accountUrl: String,
    val priority: Int,
    val protocol: String,
    val url: String
)