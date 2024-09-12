package tss.t.coreapi.models

import com.google.gson.annotations.SerializedName

data class CategoryRes(
    val count: Int,
    @SerializedName("feeds")
    val feeds: List<Category>,
) : BaseResponse() {
    data class Category(
        val id: Int,
        val name: String,
        var isFavourite: Boolean = false
    )
}