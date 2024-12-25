package tss.t.podcasts.utils.ext

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import tss.t.core.models.FavouriteDTO
import tss.t.core.repository.MediaType

internal fun MediaItem.toFavouriteDTO(): FavouriteDTO {
    val mediaItem = this
    val type = mediaTypeConvert()
    return FavouriteDTO(
        id = mediaItem.mediaId,
        type = type,
        title = mediaItem.mediaMetadata.title.toString(),
        image = mediaItem.mediaMetadata.artworkUri.toString(),
        description = mediaItem.mediaMetadata.description.toString()
    )
}

internal fun MediaItem.mediaTypeConvert(): MediaType {
    return when (mediaMetadata.mediaType) {
        MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE -> MediaType.PodcastEpisode
        MediaMetadata.MEDIA_TYPE_RADIO_STATION -> MediaType.Radio
        MediaMetadata.MEDIA_TYPE_PODCAST -> MediaType.Podcast
        MediaMetadata.MEDIA_TYPE_TV_CHANNEL -> MediaType.TVChannel
        else -> MediaType.PodcastEpisode
    }
}