package tss.t.sharedplayer.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import tss.t.sharedplayer.notifications.TSNotificationProvider
import tss.t.sharedplayer.player.PlayerManager
import javax.inject.Inject


@UnstableApi
@AndroidEntryPoint
class PlayerSessionService() : MediaSessionService() {

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var notificationProvider: TSNotificationProvider

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, playerManager.player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    1,
                    Intent("tss.t.action_from_notification").apply {
                        data = Uri.parse("tss://ts.podcast/home")
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setShowPlayButtonIfPlaybackIsSuppressed(true)
            .build()
        val channel = NotificationChannelCompat.Builder(
            "123",
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
            .setName("TsPodcast")
            .setDescription("TsPodcast app")
            .setVibrationEnabled(false)
            .setShowBadge(true)
            .build()
        NotificationManagerCompat.from(this)
            .createNotificationChannel(channel)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}