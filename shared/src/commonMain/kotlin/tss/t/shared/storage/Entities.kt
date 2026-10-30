package tss.t.shared.storage

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import tss.t.shared.favourite.FavouriteType
import tss.t.shared.model.Funding
import tss.t.shared.model.Person
import tss.t.shared.model.PodcastTranscript
import tss.t.shared.model.SocialInteract
import tss.t.shared.model.Soundbite
import tss.t.shared.model.Value

/*
 * Room rows of the iOS database (M4 §3.1). Table names, column names, affinity, NOT NULL and
 * primary keys are the same as Android `PodcastDatabase` v6
 * (`core/schemas/tss.t.core.storage.PodcastDatabase/6.json`); DatabaseSchemaParityTest checks it.
 *
 * They are separate from the API models because the columns are stricter than the JSON:
 * `Podcast.feedId` and `Episode.feedItunesId` are NOT NULL on Android but optional in the API,
 * and `Podcast` has fields (author, artwork, ...) that Android does not store.
 * See StorageMappers.kt for the mapping.
 */

/** Android `tss.t.coreapi.models.Podcast` as a row (table `Podcast`). */
@Entity(tableName = "Podcast")
data class PodcastEntity(
    val categories: Map<String, String>?,
    val dateCrawled: Long,
    val datePublished: Long,
    val datePublishedPretty: String?,
    val enclosureLength: Long,
    val enclosureType: String?,
    val enclosureUrl: String?,
    val explicit: Int,
    val feedId: Long,
    val feedImage: String?,
    val feedItunesId: Long?,
    val feedLanguage: String?,
    val feedTitle: String?,
    val guid: String?,
    @PrimaryKey
    val id: Long,
    val image: String,
    val link: String?,
    val title: String?,
    val description: String?,
    /** Epoch ms of the last write; rows expire after `PruneApiCache.TTL_MS`. */
    @ColumnInfo(defaultValue = "0")
    val cachedAt: Long = 0L,
)

/** Android `tss.t.coreapi.models.Feed` as a row (table `Feed`). */
@Entity(tableName = "Feed")
data class FeedEntity(
    val artwork: String,
    val author: String,
    val categories: Map<String, String>?,
    val contentType: String,
    val crawlErrors: Int,
    val dead: Int,
    val description: String,
    val episodeCount: Int,
    val explicit: Boolean,
    val generator: String,
    @PrimaryKey
    val id: Long,
    val image: String,
    val imageUrlHash: Long,
    val itunesId: Long?,
    val language: String,
    val lastCrawlTime: Long,
    val lastGoodHttpStatusTime: Long,
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
    val chash: String?,
    val funding: Funding?,
    val itunesType: String?,
    val value: Value?,
    @ColumnInfo(defaultValue = "0")
    val cachedAt: Long = 0L,
)

/** Android `tss.t.coreapi.models.Episode` as a row (table `Episode`). */
@Entity(tableName = "Episode")
data class EpisodeEntity(
    val chaptersUrl: String?,
    val dateCrawled: Long,
    val datePublished: Long,
    val datePublishedPretty: String,
    val description: String?,
    val duration: Long,
    val enclosureLength: Long,
    val enclosureType: String,
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
    val persons: List<Person>?,
    val podcastGuid: String,
    val season: Int,
    val socialInteract: List<SocialInteract>?,
    val soundbite: Soundbite?,
    val soundbites: List<Soundbite>?,
    val title: String,
    val transcriptUrl: String?,
    val transcripts: List<PodcastTranscript>?,
    val value: Value?,
    @ColumnInfo(defaultValue = "0")
    val cachedAt: Long = 0L,
)

/**
 * Android `tss.t.core.models.FavouriteDTO` (table `FavouriteDTO`, key `(type, id)`). [type] is
 * stored as the enum name (`"PodcastEpisode"`, `"Podcast"`), like Android's built-in enum converter.
 */
@Entity(tableName = "FavouriteDTO", primaryKeys = ["type", "id"])
data class FavouriteEntity(
    val id: String,
    val type: FavouriteType,
    val title: String?,
    val image: String?,
    val description: String?,
)

/** Android `PodcastAndEpisode`: a podcast row and every episode whose `feedId` is its `id`. */
data class PodcastWithEpisodes(
    @Embedded
    val podcast: PodcastEntity,
    @Relation(parentColumn = "id", entityColumn = "feedId")
    val episodes: List<EpisodeEntity>,
)
