package tss.t.sharedplayer.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tss.t.sharedplayer.library.LibraryIds
import tss.t.sharedplayer.library.LibraryItemNotFoundException
import tss.t.sharedplayer.library.MediaLibraryCatalog
import tss.t.sharedplayer.library.page
import tss.t.sharedplayer.notifications.TSNotificationProvider
import tss.t.sharedplayer.player.PlayerManager
import tss.t.sharedresources.R
import javax.inject.Inject


/**
 * The app's media session, also a media library (browse tree) since the car phase: Android Auto
 * (and later Android Automotive OS) binds to this service, browses [MediaLibraryCatalog] and plays
 * through the same [PlayerManager] player as the phone. See docs/planned/plans/android-auto.md.
 */
@UnstableApi
@AndroidEntryPoint
class PlayerSessionService() : MediaLibraryService() {

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var notificationProvider: TSNotificationProvider

    @Inject
    lateinit var catalog: MediaLibraryCatalog

    private var mediaSession: MediaLibrarySession? = null

    /** Browse/search work; callbacks arrive on the main thread, the catalog moves to IO itself. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaLibrarySession.Builder(this, playerManager.player, LibraryCallback())
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
            // Lock screen / system media controls: −15 s and +30 s in the back and forward
            // slots instead of previous/next, like the iOS Now Playing controls (M6).
            // Android Auto shows the same buttons in its playback view.
            .setMediaButtonPreferences(
                listOf(
                    CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
                        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                        .setSlots(CommandButton.SLOT_BACK)
                        .setDisplayName(getString(R.string.player_seek_back_15))
                        .build(),
                    CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
                        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                        .setSlots(CommandButton.SLOT_FORWARD)
                        .setDisplayName(getString(R.string.player_seek_forward_30))
                        .build(),
                )
            )
            .build()
        val channel = NotificationChannelCompat.Builder(
            TSNotificationProvider.CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
            .setName(TSNotificationProvider.CHANNEL_NAME)
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        // The service is exported (media3 and the car hosts require it). Hand the session to
        // this app, the platform media/notification controllers, the car hosts (Android Auto,
        // Android Automotive OS media center, Assistant for voice) and controllers the platform
        // trusts (system apps, MEDIA_CONTENT_CONTROL, enabled notification listeners).
        val caller = controllerInfo.packageName
        val allowed = caller == packageName ||
            caller in ALLOWED_CALLERS ||
            controllerInfo.isTrusted
        if (!allowed) Log.w(TAG, "Rejected media controller $caller")
        return if (allowed) mediaSession else null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away from Recents: when nothing plays the service stops and the process
        // may be killed right after, so save where the user is ("Nghe tiếp" on the next launch).
        playerManager.saveNow()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        // Saves the position and history, then releases the player. Through PlayerManager so
        // the next service start builds a new player instead of reusing the released one.
        playerManager.release()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // System UI / Auto ask for the "recent" root to offer playback resumption.
            val root = if (params?.isRecent == true) catalog.recentRoot() else catalog.root()
            val extras = Bundle().apply {
                // Defaults for the whole tree: shows as a grid of covers, episodes as a list.
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                )
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                )
            }
            val rootParams = LibraryParams.Builder()
                .setRecent(params?.isRecent == true)
                .setOffline(false)
                .setSuggested(false)
                .setExtras(extras)
                .build()
            return immediate(LibraryResult.ofItem(root, rootParams))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            runCatching { LibraryResult.ofItem(catalog.item(mediaId), null) }
                .getOrElse { libraryError(it) }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            runCatching {
                LibraryResult.ofItemList(catalog.children(parentId).page(page, pageSize), params)
            }.getOrElse { libraryError(it, params) }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = serviceScope.future {
            val count = runCatching { catalog.search(query).size }.getOrDefault(0)
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid(params)
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            runCatching {
                LibraryResult.ofItemList(catalog.search(query).page(page, pageSize), params)
            }.getOrElse { libraryError(it, params) }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
            catalog.resolveEach(mediaItems).toMutableList()
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> = serviceScope.future {
            catalog.resolve(mediaItems, startIndex, startPositionMs)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaItemsWithStartPosition> = serviceScope.future {
            catalog.resumption(isForPlayback)
                ?: throw UnsupportedOperationException("Nothing to resume")
        }
    }

    /** Error the car can show: "not found" for a stale id, otherwise a network message. */
    private fun <T : Any> libraryError(error: Throwable, params: LibraryParams? = null): LibraryResult<T> {
        if (error is CancellationException) throw error
        Log.w(TAG, "Library request failed", error)
        val sessionError = if (error is LibraryItemNotFoundException) {
            SessionError(SessionError.ERROR_BAD_VALUE, getString(R.string.car_error_not_found))
        } else {
            SessionError(SessionError.ERROR_IO, getString(R.string.car_error_network))
        }
        return if (params != null) {
            LibraryResult.ofError<T>(sessionError, params)
        } else {
            LibraryResult.ofError<T>(sessionError)
        }
    }

    companion object {
        private const val TAG = "PlayerSessionService"

        /** Car hosts and system callers allowed to bind besides this app and trusted controllers. */
        private val ALLOWED_CALLERS = setOf(
            "android",
            "com.android.systemui",
            // Android Auto (phone projection)
            "com.google.android.projection.gearhead",
            // Android Automotive OS media center (AOSP and Google builds)
            "com.android.car.media",
            "com.google.android.carassistant",
            // Google Assistant / Google app: "Ok Google, play ... on TSPodcast"
            "com.google.android.googlequicksearchbox",
        )
    }
}

private fun <T> immediate(value: T): ListenableFuture<T> =
    SettableFuture.create<T>().apply { set(value) }

/**
 * Runs [block] in this scope and exposes it as the [ListenableFuture] media3 callbacks return.
 * Cancelling the future cancels the coroutine; an exception fails the future.
 */
private fun <T> CoroutineScope.future(block: suspend () -> T): ListenableFuture<T> {
    val result = SettableFuture.create<T>()
    val job = launch {
        try {
            result.set(block())
        } catch (e: CancellationException) {
            result.cancel(false)
            throw e
        } catch (t: Throwable) {
            result.setException(t)
        }
    }
    result.addListener({ if (result.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
    return result
}
