package tss.t.ads

import android.os.Bundle
import androidx.core.os.bundleOf
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError

fun MaxAd.toBundle(): Bundle {
    val ad = this
    return bundleOf(
        "adUnitId" to ad.adUnitId,
        "width" to ad.size.width,
        "height" to ad.size.height,
        "isFullscreenAd" to ad.format.isFullscreenAd,
        "isBannerOrLeaderAd" to ad.format.isBannerOrLeaderAd,
        "dspId" to ad.dspId,
        "dspName" to ad.dspName,
        "networkName" to ad.networkName,
        "networkPlacement" to ad.networkPlacement,
        "placement" to ad.placement,
        "waterfallName" to ad.waterfall.name,
    )
}

fun MaxAd.toArray(): Array<Pair<String, Any?>> {
    val ad = this
    return arrayOf(
        "adUnitId" to ad.adUnitId,
        "width" to ad.size.width,
        "height" to ad.size.height,
        "isFullscreenAd" to ad.format.isFullscreenAd,
        "isBannerOrLeaderAd" to ad.format.isBannerOrLeaderAd,
        "dspId" to ad.dspId,
        "dspName" to ad.dspName,
        "networkName" to ad.networkName,
        "networkPlacement" to ad.networkPlacement,
        "placement" to ad.placement,
        "waterfallName" to ad.waterfall.name,
    )
}

fun MaxError.toArray(): Array<Pair<String, Any?>> {
    val error = this
    return arrayOf(
        "message" to error.message,
        "errorCode" to error.code,
        "mediatedNetworkErrorCode" to error.mediatedNetworkErrorCode,
        "mediatedNetworkErrorMessage" to error.mediatedNetworkErrorMessage,
        "requestLatencyMillis" to error.requestLatencyMillis
    )
}