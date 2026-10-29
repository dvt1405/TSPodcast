package tss.t.podcast.ads

import androidx.media3.common.Player
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tss.t.ads.AdsHostSignals
import tss.t.core.storage.SharedPref
import tss.t.core.storage.hasSelectFavouriteCategory
import tss.t.core.storage.isOnboardingFinished
import tss.t.podcast.ui.navigations.TSRouter
import tss.t.sharedplayer.controller.TSMediaController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the ad layer what the app is doing, so `:ads` never depends on the
 * player or navigation. Called on the main thread (MediaController requires it).
 */
@Singleton
class AppAdsHostSignals @Inject constructor(
    private val mediaController: TSMediaController,
    private val sharedPref: SharedPref,
) : AdsHostSignals {

    /** Current top-level route, updated by MainActivity's NavController listener. */
    @Volatile
    var currentRoute: String? = null

    override fun isPlaybackActive(): Boolean {
        val player = mediaController.sessionController ?: return false
        // playWhenReady covers "just pressed Play" and buffering before audio starts.
        return player.isPlaying ||
                (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) ||
                (player.playWhenReady && player.playbackState == Player.STATE_READY)
    }

    override fun isOnPlayerScreen(): Boolean = currentRoute == TSRouter.Player.route

    override fun isOnboardingCompleted(): Boolean =
        sharedPref.isOnboardingFinished() && sharedPref.hasSelectFavouriteCategory()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdsHostModule {
    @Binds
    abstract fun bindHostSignals(impl: AppAdsHostSignals): AdsHostSignals
}
