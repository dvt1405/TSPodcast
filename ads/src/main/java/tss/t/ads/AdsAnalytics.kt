package tss.t.ads

import tss.t.sharedfirebase.TSAnalytics
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Ad analytics without SDK types. Event names follow
 * docs/plans/admob-migration.md §3.6. Revenue (`ad_impression` with value) is
 * logged by GA4 itself once the AdMob app is linked to Firebase; `ads_paid`
 * here is for reconciling against the AdMob API.
 *
 * Safe to call from any thread (TSAnalytics logs on its own IO scope).
 */
interface AdsAnalytics {
    fun request(placement: AdPlacement)
    fun loadFailed(placement: AdPlacement, code: String)
    fun show(placement: AdPlacement)
    fun impression(placement: AdPlacement)
    fun click(placement: AdPlacement)
    fun blocked(placement: AdPlacement, reason: String)
    fun paid(placement: AdPlacement, valueMicros: Long, currency: String, precision: String)
    fun rewardEarned(placement: AdPlacement)
    fun consent(status: String, canRequestAds: Boolean, errorCode: Int?)
}

@Singleton
class FirebaseAdsAnalytics @Inject constructor(
    private val analytics: TSAnalytics,
) : AdsAnalytics {
    private fun log(event: String, placement: AdPlacement?, vararg extra: Pair<String, Any?>) {
        val props = buildList {
            if (placement != null) {
                add("placement" to placement.key)
                add("format" to placement.format.name.lowercase())
            }
            addAll(extra)
        }
        analytics.trackEvent(event, analytics.currentScreenName, *props.toTypedArray())
    }

    override fun request(placement: AdPlacement) = log("ads_request", placement)
    override fun loadFailed(placement: AdPlacement, code: String) =
        log("ads_load_failed", placement, "code" to code)

    override fun show(placement: AdPlacement) = log("ads_show", placement)
    override fun impression(placement: AdPlacement) = log("ads_impression", placement)
    override fun click(placement: AdPlacement) = log("ads_click", placement)

    /** Sampled at 10%: a cap can fire on every foreground. */
    override fun blocked(placement: AdPlacement, reason: String) {
        if (Random.nextInt(10) == 0) log("ads_blocked", placement, "reason" to reason)
    }

    override fun paid(placement: AdPlacement, valueMicros: Long, currency: String, precision: String) =
        log(
            "ads_paid", placement,
            "value_micros" to valueMicros, "currency" to currency, "precision" to precision
        )

    override fun rewardEarned(placement: AdPlacement) = log("ads_rewarded_earned", placement)

    override fun consent(status: String, canRequestAds: Boolean, errorCode: Int?) =
        log(
            "consent_status", null,
            "status" to status, "can_request_ads" to canRequestAds.toString(),
            "error_code" to errorCode
        )
}
