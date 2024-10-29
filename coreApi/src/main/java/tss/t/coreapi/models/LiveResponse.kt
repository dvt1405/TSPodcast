package tss.t.coreapi.models

data class LiveResponse(
    val count: Int,
    val items: List<LiveEpisode>,
    val max: Int?,
) : BaseResponse() {
}