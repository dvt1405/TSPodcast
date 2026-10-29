package tss.t.shared.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

/**
 * Response of `podcasts/trending` (E1) and `recent/feeds` (E5).
 * The JSON key is `feeds`; the property keeps Android's name `items`.
 */
@Serializable
data class TrendingPodcastRes(
    val count: Int = 0,
    @SerialName("feeds")
    val items: List<Podcast> = emptyList(),
    val max: Int? = null,
)

/** Response of `episodes/live` (E2). */
@Serializable
data class LiveResponse(
    val count: Int = 0,
    val items: List<LiveEpisode> = emptyList(),
    val max: Int? = null,
)

/** Response of `search/byterm` (E7), `search/music/byterm` (E8) and `podcasts/bymedium` (E6). */
@Serializable
data class SearchResponse(
    val count: Int = 0,
    val feeds: List<Feed> = emptyList(),
    val query: String? = null,
)

/** Response of `episodes/byfeedid` (E10). */
@Serializable
data class EpisodeResponse(
    val count: Int = 0,
    val items: List<Episode> = emptyList(),
    val liveItems: List<LiveEpisode>? = null,
    val query: String? = null,
)

/**
 * Response of `podcasts/byfeedid` (E11).
 *
 * For an unknown id PodcastIndex answers `"feed": []`; that is read as `feed = null`
 * instead of failing the whole response.
 */
@Serializable
data class PodcastByFeedIdRes(
    @Serializable(with = FeedOrEmptySerializer::class)
    val feed: Feed? = null,
    val query: Query? = null,
) {
    @Serializable
    data class Query(
        val id: String = "",
    )
}

/** Response of `categories/list` (E9). */
@Serializable
data class CategoryRes(
    val count: Int = 0,
    val feeds: List<Category> = emptyList(),
) {
    @Serializable
    data class Category(
        val id: Int = 0,
        val name: String = "",
        val isFavourite: Boolean? = null,
    )

    companion object {
        /**
         * Parses the bundled `categories.json` (`{"Categories":[{"id":1,"name":"Arts"}, …]}`),
         * the fallback Android reads in `PodcastsRepositoryImpl.getCategoriesFromAsset`.
         */
        fun fromAssetJson(json: Json, text: String): CategoryRes {
            val categories = json.decodeFromString(CategoriesAsset.serializer(), text).categories
            return CategoryRes(count = categories.size, feeds = categories)
        }
    }
}

@Serializable
private class CategoriesAsset(
    @SerialName("Categories")
    val categories: List<CategoryRes.Category> = emptyList(),
)

/** Reads a feed object, or null when the API sends anything else (`[]`, `null`). */
@OptIn(ExperimentalSerializationApi::class)
internal object FeedOrEmptySerializer : KSerializer<Feed?> {
    override val descriptor: SerialDescriptor = Feed.serializer().descriptor.nullable

    override fun deserialize(decoder: Decoder): Feed? {
        val jsonDecoder = decoder as? JsonDecoder ?: return Feed.serializer().deserialize(decoder)
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonObject) return null
        return jsonDecoder.json.decodeFromJsonElement(Feed.serializer(), element)
    }

    override fun serialize(encoder: Encoder, value: Feed?) {
        if (value == null) encoder.encodeNull() else Feed.serializer().serialize(encoder, value)
    }
}
