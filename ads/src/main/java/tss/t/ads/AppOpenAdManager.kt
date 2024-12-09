package tss.t.ads

import android.content.Context
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import dagger.hilt.android.qualifiers.ApplicationContext
import tss.t.core.storage.SharedPref
import tss.t.core.storage.hasSelectFavouriteCategory
import tss.t.core.storage.isOnboardingFinished
import tss.t.securedtoken.NativeLib
import tss.t.sharedfirebase.TSAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpenAdManager @Inject constructor(
    @ApplicationContext
    private val applicationContext: Context,
    private val tsAnalytics: TSAnalytics,
    private val sharedPref: SharedPref
) : MaxAdListener {

    val adEnabled: Boolean
        get() = sharedPref.isOnboardingFinished() && sharedPref.hasSelectFavouriteCategory()

    private val showAdThreshHold by lazy {
        AdsConstants.A_MINUTES * 10
    }

    private var lastShowAd: Long = 0L
    private var appOpenAd: MaxAppOpenAd? = null

    init {
        instance = this
    }

    fun showAdIfReady() {
        if (System.currentTimeMillis() - lastShowAd < showAdThreshHold || appOpenAd?.isReady == true) {
            return
        }
        tsAnalytics.trackEvent(AdsConstants.EVENT_NAME_LOAD_AD_APP_OPEN)
        appOpenAd = MaxAppOpenAd(
            NativeLib.getAdAppOpenId(),
            applicationContext
        )
        appOpenAd?.setListener(this)
        appOpenAd?.loadAd()
    }

    override fun onAdLoaded(ad: MaxAd) {
        appOpenAd?.showAd()
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_APP_OPEN_LOADED,
            prop = ad.toArray()
        )
    }

    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_APP_OPEN_LOAD_FAILED,
            screenName = tsAnalytics.currentScreenName,
            "adUnitId" to adUnitId,
            *error.toArray()
        )
    }

    override fun onAdDisplayed(ad: MaxAd) {
        lastShowAd = System.currentTimeMillis()
        appOpenAd?.setListener(null)
        appOpenAd = null
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_APP_OPEN_DISPLAYED,
            prop = ad.toArray()
        )
    }

    override fun onAdClicked(ad: MaxAd) {
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_APP_OPEN_CLICKED,
            prop = ad.toArray()
        )
    }

    override fun onAdHidden(ad: MaxAd) {
        appOpenAd?.loadAd()
    }

    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
        appOpenAd?.loadAd()
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_APP_OPEN_DISPLAY_FAILED,
            screenName = tsAnalytics.currentScreenName,
            *ad.toArray(),
            *error.toArray()
        )
    }

    companion object {
        var instance: AppOpenAdManager? = null
    }
}