package tss.t.coreapi.models

import com.google.gson.annotations.SerializedName

data class SearchResponse(
    @SerializedName("count")
    val count: Int,
    @SerializedName("feeds")
    val feeds: List<Feed>,
    @SerializedName("query")
    val query: String,
) : BaseResponse() {
}