package tss.t.sharedplayer.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.R
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import dagger.hilt.android.qualifiers.ApplicationContext
import tss.t.coreapi.Constants
import tss.t.sharedlibrary.utils.notifications.NotificationUtils
import java.util.Arrays
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class TSNotificationProvider @Inject constructor(
    @ApplicationContext
    private val context: Context
) : MediaNotification.Provider {
    private var pendingOnBitmapLoadedFutureCallback: OnBitmapLoadedFutureCallback? = null
    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val currentMediaItem = mediaSession.player.currentMediaItem!!
        val channelId = "123"
        NotificationUtils.createChannelIfNeeded(context, channelId)
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            Notification.Builder(context)
        }
        val customLayoutWithEnabledCommandButtonsOnly =
            ImmutableList.Builder<CommandButton>()
        for (i in customLayout.indices) {
            val button = customLayout[i]
            if (button.sessionCommand != null && button.sessionCommand!!.commandCode == SessionCommand.COMMAND_CODE_CUSTOM && button.isEnabled) {
                customLayoutWithEnabledCommandButtonsOnly.add(customLayout[i])
            }
        }
        val player = mediaSession.player
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(context, channelId)
        val notificationId: Int = getNotificationId(mediaSession)

        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(mediaSession)
        val compactViewIndices: IntArray =
            addNotificationActions(
                mediaSession,
                getMediaButtons(
                    mediaSession,
                    player.availableCommands,
                    customLayoutWithEnabledCommandButtonsOnly.build(),
                    !Util.shouldShowPlayButton(
                        player, mediaSession.showPlayButtonIfPlaybackIsSuppressed
                    )
                ),
                builder,
                actionFactory
            )
        mediaStyle.setShowActionsInCompactView(*compactViewIndices)


        // Set metadata info in the notification.
        if (player.isCommandAvailable(Player.COMMAND_GET_METADATA)) {
            val metadata = player.mediaMetadata
            builder
                .setContentTitle(getNotificationContentTitle(metadata))
                .setContentText(getNotificationContentText(metadata))
            val bitmapFuture = mediaSession.bitmapLoader.loadBitmapFromMetadata(metadata)
            if (bitmapFuture != null) {
                pendingOnBitmapLoadedFutureCallback?.discardIfPending()
                if (bitmapFuture.isDone) {
                    try {
                        builder.setLargeIcon(Futures.getDone(bitmapFuture))
                    } catch (e: CancellationException) {
                        Log.w(
                            TAG,
                            getBitmapLoadErrorMessage(e)
                        )
                    } catch (e: ExecutionException) {
                        Log.w(
                            TAG,
                            getBitmapLoadErrorMessage(e)
                        )
                    }
                } else {
                    pendingOnBitmapLoadedFutureCallback = OnBitmapLoadedFutureCallback(
                        notificationId, builder, onNotificationChangedCallback
                    )
                    Futures.addCallback<Bitmap>(
                        bitmapFuture,
                        pendingOnBitmapLoadedFutureCallback!!
                    )  // This callback must be executed on the next looper iteration, after this method has
                    // returned a media notification.
                    { r: Runnable? ->
                        mHandler.post(r!!)
                    }
                }
            }
        }

        if (player.isCommandAvailable(Player.COMMAND_STOP) || Util.SDK_INT < 21) {
            // We must include a cancel intent for pre-L devices.
            mediaStyle.setCancelButtonIntent(
                actionFactory.createMediaActionPendingIntent(
                    mediaSession,
                    Player.COMMAND_STOP.toLong()
                )
            )
        }

        val playbackStartTimeMs = getPlaybackStartTimeEpochMs(player)
        val displayElapsedTimeWithChronometer = playbackStartTimeMs != C.TIME_UNSET
        builder
            .setWhen(if (displayElapsedTimeWithChronometer) playbackStartTimeMs else 0L)
            .setShowWhen(displayElapsedTimeWithChronometer)
            .setUsesChronometer(displayElapsedTimeWithChronometer)

        if (Util.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        mediaSession.setSessionActivity(
            PendingIntent.getActivity(
                context,
                1,
                Intent(Constants.ACTION_START_FROM_NOTIFICATION).apply {
                    data =
                        Uri.parse("${Constants.DEEPLINK_CURRENT_PLAYING}?${Constants.QUERY_MEDIA_ITEM_NAME}=${currentMediaItem.mediaId}")
                },
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        val notification: Notification =
            builder
                .setContentIntent(mediaSession.sessionActivity)
                .setDeleteIntent(
                    actionFactory.createMediaActionPendingIntent(
                        mediaSession,
                        Player.COMMAND_STOP.toLong()
                    )
                )
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.media3_notification_small_icon)
                .setStyle(mediaStyle)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(false)
                .setGroup(DefaultMediaNotificationProvider.GROUP_KEY)
                .build()
        return MediaNotification(notificationId, notification)
    }

    private fun getBitmapLoadErrorMessage(e: Exception): String {
        return "Load bitmap error: ${e.message}"
    }

    private fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? {
        return metadata.title;
    }

    private fun getNotificationId(mediaSession: MediaSession): Int {
        return 12321
    }

    protected fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
        return metadata.artist
    }

    private fun getPlaybackStartTimeEpochMs(player: Player): Long {
        return if (Util.SDK_INT >= 21 && player.isPlaying
            && !player.isPlayingAd
            && !player.isCurrentMediaItemDynamic
            && player.playbackParameters.speed == 1f
        ) {
            System.currentTimeMillis() - player.contentPosition
        } else {
            C.TIME_UNSET
        }
    }

    protected fun addNotificationActions(
        mediaSession: MediaSession?,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory
    ): IntArray {
        var compactViewIndices = IntArray(3)
        val defaultCompactViewIndices = IntArray(3)
        Arrays.fill(compactViewIndices, C.INDEX_UNSET)
        Arrays.fill(defaultCompactViewIndices, C.INDEX_UNSET)
        var compactViewCommandCount = 0
        for (i in mediaButtons.indices) {
            val commandButton = mediaButtons[i]
            if (commandButton.sessionCommand != null) {
                builder.addAction(
                    actionFactory.createCustomActionFromCustomCommandButton(
                        mediaSession!!,
                        commandButton
                    )
                )
            } else {
                Assertions.checkState(commandButton.playerCommand != Player.COMMAND_INVALID)
                builder.addAction(
                    actionFactory.createMediaAction(
                        mediaSession!!,
                        IconCompat.createWithResource(context, commandButton.iconResId),
                        commandButton.displayName,
                        commandButton.playerCommand
                    )
                )
            }
            if (compactViewCommandCount == 3) {
                continue
            }
            val compactViewIndex =
                commandButton.extras.getInt(
                    DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX,  /* defaultValue= */
                    C.INDEX_UNSET
                )
            if (compactViewIndex >= 0 && compactViewIndex < compactViewIndices.size) {
                compactViewCommandCount++
                compactViewIndices[compactViewIndex] = i
            } else if (commandButton.playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS
                || commandButton.playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            ) {
                defaultCompactViewIndices[0] = i
            } else if (commandButton.playerCommand == Player.COMMAND_PLAY_PAUSE) {
                defaultCompactViewIndices[1] = i
            } else if (commandButton.playerCommand == Player.COMMAND_SEEK_TO_NEXT
                || commandButton.playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
            ) {
                defaultCompactViewIndices[2] = i
            }
        }
        if (compactViewCommandCount == 0) {
            // If there is no custom configuration we use the seekPrev (if any), play/pause (if any),
            // seekNext (if any) action in compact view.
            var indexInCompactViewIndices = 0
            for (i in defaultCompactViewIndices.indices) {
                if (defaultCompactViewIndices[i] == C.INDEX_UNSET) {
                    continue
                }
                compactViewIndices[indexInCompactViewIndices] = defaultCompactViewIndices[i]
                indexInCompactViewIndices++
            }
        }
        var i = 0
        while (i < compactViewIndices.size) {
            if (compactViewIndices[i] == C.INDEX_UNSET) {
                compactViewIndices = compactViewIndices.copyOf(i)
                break
            }
            i++
        }
        return compactViewIndices
    }

    private fun getMediaButtons(
        session: MediaSession?,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        // Skip to previous action.
        val commandButtons = ImmutableList.Builder<CommandButton>()
        if (playerCommands.containsAny(
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            )
        ) {
            val commandButtonExtras = Bundle()
            commandButtonExtras.putInt(
                DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX,
                C.INDEX_UNSET
            )
            commandButtons.add(
                CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .setDisplayName(
                        context.getString(R.string.media3_controls_seek_to_previous_description)
                    )
                    .setExtras(commandButtonExtras)
                    .build()
            )
        }
        if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
            val commandButtonExtras = Bundle()
            commandButtonExtras.putInt(
                DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX,
                C.INDEX_UNSET
            )
            if (showPauseButton) {
                commandButtons.add(
                    CommandButton.Builder(CommandButton.ICON_PAUSE)
                        .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                        .setExtras(commandButtonExtras)
                        .setDisplayName(context.getString(R.string.media3_controls_pause_description))
                        .build()
                )
            } else {
                commandButtons.add(
                    CommandButton.Builder(CommandButton.ICON_PLAY)
                        .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                        .setExtras(commandButtonExtras)
                        .setDisplayName(context.getString(R.string.media3_controls_play_description))
                        .build()
                )
            }
        }
        // Skip to next action.
        if (playerCommands.containsAny(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
            )
        ) {
            val commandButtonExtras = Bundle()
            commandButtonExtras.putInt(
                DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX,
                C.INDEX_UNSET
            )
            commandButtons.add(
                CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .setExtras(commandButtonExtras)
                    .setDisplayName(context.getString(R.string.media3_controls_seek_to_next_description))
                    .build()
            )
        }
        for (i in customLayout.indices) {
            val button = customLayout[i]
            if (button.sessionCommand != null
                && button.sessionCommand!!.commandCode == SessionCommand.COMMAND_CODE_CUSTOM
            ) {
                commandButtons.add(button)
            }
        }
        return commandButtons.build()
    }

    class OnBitmapLoadedFutureCallback(
        private val notificationId: Int,
        private val builder: NotificationCompat.Builder,
        private val onNotificationChangedCallback: MediaNotification.Provider.Callback
    ) : FutureCallback<Bitmap?> {
        private var discarded = false

        fun discardIfPending() {
            discarded = true
        }

        override fun onSuccess(result: Bitmap?) {
            if (!discarded) {
                builder.setLargeIcon(result)
                onNotificationChangedCallback.onNotificationChanged(
                    MediaNotification(notificationId, builder.build())
                )
            }
        }

        override fun onFailure(t: Throwable) {
            if (!discarded) {
                Log.w(
                    "TuanDv",
                    "Load bitmap error: ${t.message}"
                )
            }
        }
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean {
        return true
    }

    companion object {
        val mHandler by lazy { Handler(Looper.getMainLooper()) }
        const val TAG = "TuanDv"
    }
}