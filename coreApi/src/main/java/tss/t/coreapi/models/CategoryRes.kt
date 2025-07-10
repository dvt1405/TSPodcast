package tss.t.coreapi.models

data class CategoryRes(
    //@SerializedName("count")
    val count: Int,
    //@SerializedName("feeds")
    val feeds: List<Category>,
) : BaseResponse() {
    data class Category(
        //@SerializedName("id")
        val id: Int,
        //@SerializedName("name")
        val name: String,
        val isFavourite: Boolean? = null
    )
}