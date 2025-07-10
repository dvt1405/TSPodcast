package tss.t.coreapi.models

data class LiveResponse(
    //@SerializedName("count")
    val count: Int,
    //@SerializedName("items")
    val items: List<LiveEpisode>,
    //@SerializedName("max")
    val max: Int?,
) : BaseResponse() {
}