package tss.t.sharedplayer.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class TSNotificationProvider @Inject constructor(
    @ApplicationContext
    private val context: Context
) : MediaNotification.Provider {
    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val currentMediaItem = mediaSession.player.currentMediaItem!!
        return MediaNotification(
            10, Notification.Builder(context, "123")
                .setStyle(Notification.MediaStyle())
                .setContentTitle(currentMediaItem.mediaMetadata.title)
                .setContentText(currentMediaItem.mediaMetadata.description)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        10,
                        Intent("tss://ts.podcast//playing/${currentMediaItem.mediaId}"),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setColorized(true)
                .build()
        )
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean {
        TODO("Not yet implemented")
    }
}