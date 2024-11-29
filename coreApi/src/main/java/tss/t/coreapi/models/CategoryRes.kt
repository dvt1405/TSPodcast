package tss.t.coreapi.models

import com.google.gson.annotations.SerializedName

data class CategoryRes(
    @SerializedName("count")
    val count: Int,
    @SerializedName("feeds")
    val feeds: List<Category>,
) : BaseResponse() {
    data class Category(
        @SerializedName("id")
        val id: Int,
        @SerializedName("name")
        val name: String,
        @SerializedName("isFavourite")
        var isFavourite: Boolean = false
    )
}