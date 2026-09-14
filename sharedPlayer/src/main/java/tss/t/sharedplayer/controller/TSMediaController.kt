package tss.t.sharedplayer.controller

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tss.t.sharedlibrary.crash.safeCallOrNull
import tss.t.sharedplayer.service.PlayerSessionService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TSMediaController @Inject constructor(
    @ApplicationContext
    private val context: Context
) : ActivityLifecycleCallbacks {
    private val coroutineScope by lazy {
        CoroutineScope(Dispatchers.Default)
    }
    private var sessionToken: SessionToken? = null
    private var _sessionController: MediaController? = null
    private var mediaBrowser: MediaBrowser? = null

    val sessionController: MediaController?
        get() = _sessionController

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.getLocalClassName() == "MainActivity") {
            coroutineScope.launch {
                if (sessionToken != null) return@launch
                val token = SessionToken(
                    context,
                    ComponentName(context, PlayerSessionService::class.java)
                )
                sessionToken = token

                // Future.get() throws ExecutionException when the service fails
                // to connect. On directExecutor that ran on whichever thread
                // completed the future, uncaught. Hop to the main executor and
                // report instead of crashing.
                val mainExecutor = ContextCompat.getMainExecutor(context)
                val controllerFuture = MediaController.Builder(context, token).buildAsync()
                controllerFuture.addListener({
                    _sessionController = safeCallOrNull(TAG) { controllerFuture.get() }
                }, mainExecutor)
                val browserFuture = MediaBrowser.Builder(context, token).buildAsync()
                browserFuture.addListener({
                    mediaBrowser = safeCallOrNull(TAG) { browserFuture.get() }
                }, mainExecutor)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    companion object {
        private const val TAG = "TSMediaController"
    }
}