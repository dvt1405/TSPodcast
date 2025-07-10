package tss.t.coreapi.models

data class SearchResponse(
    //@SerializedName("count")
    val count: Int,
    //@SerializedName("feeds")
    val feeds: List<Feed>,
    //@SerializedName("query")
    val query: String,
) : BaseResponse() {
}