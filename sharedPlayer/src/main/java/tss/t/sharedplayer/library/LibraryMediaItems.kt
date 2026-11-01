package tss.t.sharedplayer.library

import android.net.Uri
import android.os.Bundle
import androidx.core.text.HtmlCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import tss.t.coreapi.Constants
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast
import tss.t.sharedplayer.player.PlayerManager

/**
 * The [MediaItem] the player plays for an episode. The phone (`Episode.toMediaItem` in the app)
 * and the car browse tree both build it here, so an episode started in the car looks the same to
 * the phone UI (same id, same extras, same show id for the speed profile).
 */
fun Episode.toPlayableMediaItem(album: CharSequence? = null): MediaItem {
    val html = HtmlCompat.fromHtml(description ?: "", HtmlCompat.FROM_HTML_MODE_COMPACT)
    return MediaItem.Builder()
        .setMediaId("$id")
        .setUri(enclosureUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(persons?.firstOrNull()?.name ?: album)
                .setDescription(html)
                .setSubtitle(html)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .setDisplayTitle(title)
                .setIsPlayable(true)
                .setArtworkUri(Uri.parse(getImageUrl()))
                // Kept as the app always set it (PodcastIndex sends seconds); the player reads
                // the real duration from the stream. See docs/planned/plans/android-auto.md §8.
                .setDurationMs(duration)
                .setAlbumTitle(album)
                .setExtras(
                    Bundle().apply {
                        putString(Constants.EXTRA_MEDIA_TYPE_KEY, Constants.MEDIA_TYPE_EPISODE)
                        putLong(PlayerManager.EXTRA_FEED_ID, feedId)
                    }
                )
                .build()
        )
        .build()
}

/**
 * An episode row in the car browse list: short text only (Auto truncates and must not show long
 * HTML while driving) and the listening progress as a completion badge.
 *
 * @param resumePositionMs saved position of this episode, 0 when never started or finished.
 */
internal fun Episode.toBrowseItem(showTitle: CharSequence?, resumePositionMs: Long): MediaItem {
    val extras = Bundle()
    val durationMs = duration * 1000L
    if (resumePositionMs > 0L) {
        extras.putInt(
            MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
            MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
        )
        if (durationMs > 0L) {
            extras.putDouble(
                MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE,
                (resumePositionMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
            )
        }
    } else {
        extras.putInt(
            MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
            MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
        )
    }
    extras.putString(Constants.EXTRA_MEDIA_TYPE_KEY, Constants.MEDIA_TYPE_EPISODE)
    extras.putLong(PlayerManager.EXTRA_FEED_ID, feedId)
    return MediaItem.Builder()
        .setMediaId("$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .setArtist(showTitle)
                .setSubtitle(showTitle)
                .setAlbumTitle(showTitle)
                .setArtworkUri(Uri.parse(getImageUrl()))
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(extras)
                .build()
        )
        .build()
}

/** A show: browsable, its children are its episodes. */
internal fun podcastNode(feedId: Long, title: String?, artwork: String?): MediaItem =
    MediaItem.Builder()
        .setMediaId(LibraryIds.podcast(feedId))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .setArtworkUri(artwork?.takeIf { it.isNotBlank() }?.let(Uri::parse))
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .build()
        )
        .build()

internal fun Podcast.toNode(): MediaItem = podcastNode(
    feedId = id,
    title = title ?: feedTitle,
    artwork = image.ifBlank { feedImage }
)

/**
 * A folder (root or a root tab). [childrenStyle] is the content style of its children, e.g.
 * [MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM] for a wall of show covers.
 */
internal fun folderNode(
    mediaId: String,
    title: CharSequence,
    mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    childrenStyle: Int? = null,
): MediaItem {
    val extras = Bundle()
    if (childrenStyle != null) {
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, childrenStyle)
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, childrenStyle)
    }
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .setMediaType(mediaType)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .setExtras(extras)
                .build()
        )
        .build()
}
