package tss.t.shared.model

import kotlinx.serialization.Serializable

/**
 * A live item of `episodes/live` (Android: `tss.t.coreapi.models.LiveEpisode`).
 *
 * Live items often omit `duration`, `season`, `episodeType` and friends, so all fields
 * are optional. `explicit` is an Int (0/1).
 */
@Serializable
data class LiveEpisode(
    @Serializable(with = CategoriesSerializer::class)
    val categories: Categories? = null,
    val chaptersUrl: String? = null,
    val contentLink: String = "",
    val dateCrawled: Long = 0L,
    val datePublished: Long = 0L,
    val datePublishedPretty: String = "",
    val description: String? = null,
    val duration: Long = 0L,
    val enclosureLength: Long = 0L,
    val enclosureType: String = "",
    val enclosureUrl: String = "",
    val endTime: Long = 0L,
    val episode: Long = 0L,
    val episodeType: String? = null,
    val explicit: Int = 0,
    val feedDead: Int = 0,
    val feedDuplicateOf: Long = 0L,
    val feedId: Long = 0L,
    val feedImage: String = "",
    val feedItunesId: Long? = null,
    val feedLanguage: String? = null,
    val feedTitle: String? = null,
    val guid: String = "",
    val id: Long = 0L,
    val image: String = "",
    val link: String = "",
    val season: Int = 0,
    val startTime: Long = 0L,
    val status: String = "",
    val title: String = "",
    val transcriptUrl: String? = null,
)
