package tss.t.coreapi.models

import com.google.gson.annotations.SerializedName

data class LiveResponse(
    @SerializedName("count")
    val count: Int,
    @SerializedName("items")
    val items: List<LiveEpisode>,
    @SerializedName("max")
    val max: Int?,
) : BaseResponse() {
}