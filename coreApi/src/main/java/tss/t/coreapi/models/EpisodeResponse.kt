package tss.t.coreapi.models

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

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

