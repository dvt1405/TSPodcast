package tss.t.coreapi.models

data class PodcastByFeedIdRes(
    //@SerializedName("feed")
    val feed: Feed,
    //@SerializedName("query")
    val query: Query,
) : BaseResponse() {
    data class Query(
        //@SerializedName("id")
        val id: String
    )
}