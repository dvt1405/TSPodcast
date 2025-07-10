package tss.t.coreapi.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.IgnoredOnParcel

data class TrendingPodcastRes(
    @SerializedName("count")
    val count: Int,
    @SerializedName("feeds")
    val items: List<Podcast>,
    @SerializedName("max")
    val max: Int?
) : BaseResponse() {

    @IgnoredOnParcel
    @Expose(deserialize = false, serialize = false)
    var type: Type? = Type.Trending

    enum class Type {
        Trending,
        RecentFeed,
        RecentNewFeed
    }
}