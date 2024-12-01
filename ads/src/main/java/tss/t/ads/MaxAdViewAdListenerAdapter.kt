package tss.t.ads

import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import tss.t.sharedfirebase.TSAnalytics

open class MaxAdViewAdListenerAdapter(
    private val tsAnalytics: TSAnalytics
) : MaxAdViewAdListener {
    override fun onAdLoaded(ad: MaxAd) {
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_BANNER_LOADED,
            prop = ad.toArray()
        )
    }

    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_BANNER_LOAD_FAILED,
            screenName = tsAnalytics.currentScreenName,
            "adUnitId" to adUnitId,
            *error.toArray()
        )
    }

    override fun onAdDisplayed(ad: MaxAd) {
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_BANNER_DISPLAYED,
            prop = ad.toArray()
        )
    }

    override fun onAdClicked(ad: MaxAd) {
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_BANNER_CLICKED,
            prop = ad.toArray()
        )
    }

    override fun onAdHidden(ad: MaxAd) {
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_BANNER_HIDDEN,
            prop = ad.toArray()
        )
    }

    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_BANNER_DISPLAY_FAILED,
            screenName = tsAnalytics.currentScreenName,
            *ad.toArray(),
            *error.toArray()
        )
    }

    override fun onAdExpanded(ad: MaxAd) {
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_BANNER_EXPANDED,
            screenName = tsAnalytics.currentScreenName,
            *ad.toArray(),
        )
    }

    override fun onAdCollapsed(ad: MaxAd) {
        tsAnalytics.trackEvent(
            AdsConstants.EVENT_NAME_AD_BANNER_COLLAPSED,
            screenName = tsAnalytics.currentScreenName,
            *ad.toArray(),
        )
    }
}