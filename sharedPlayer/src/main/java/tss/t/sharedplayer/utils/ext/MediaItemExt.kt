package tss.t.sharedplayer.utils.ext

import androidx.media3.common.MediaItem

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