package tss.t.coreapi.models

data class SearchResponse(
    val count: Int,
    val feeds: List<Feed>,
    val query: String,
) : BaseResponse() {
}