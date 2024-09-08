package tss.t.coreapi.models

data class PodcastByFeedIdRes(
    val feed: Feed,
    val query: Query,
) : BaseResponse() {
    data class Query(val id: String)
}