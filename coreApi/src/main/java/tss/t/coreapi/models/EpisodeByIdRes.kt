package tss.t.coreapi.models

/** `episodes/byid`: `{"status": "true", "episode": {...}}`. */
data class EpisodeByIdRes(
    val episode: Episode?,
) : BaseResponse()
