package tss.t.coreapi.models

data class Category(
    val count: Int,
    val feeds: List<Category.Feed>,
) : BaseResponse() {
    data class Feed(
        val id: Int,
        val name: String
    )
}