package tss.t.sharedplayer.utils.ext

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import tss.t.core.repository.MediaType

val MediaItem.image
    get() = this.mediaMetadata
        .artworkUri
        .toString()


val MediaItem.feedImage
    get() = this.mediaMetadata
        .artworkUri
        .toString()

val MediaItem.link
    get() = this.localConfiguration
        ?.uri
        .toString()

val MediaItem.title
    get() = this.mediaMetadata
        .title
        ?.toString()

val MediaItem.description
    get() = this.mediaMetadata
        .description
        ?.toString()

val MediaItem.album
    get() = this.mediaMetadata
        .albumTitle
        ?.toString()

val MediaItem.artist
    get() = this.mediaMetadata
        .artist
        ?.toString()

val MediaItem.mediaType
    get() = when (this.mediaMetadata.mediaType) {
        MediaMetadata.MEDIA_TYPE_RADIO_STATION -> MediaType.Radio
        MediaMetadata.MEDIA_TYPE_TV_CHANNEL -> MediaType.TVChannel
        MediaMetadata.MEDIA_TYPE_PODCAST -> MediaType.Podcast
        else -> MediaType.PodcastEpisode
    }
