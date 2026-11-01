package tss.t.ads

import tss.t.sharedlibrary.analytics.AnalyticsEvent
import tss.t.sharedlibrary.analytics.AnalyticsTracker
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Ad analytics without SDK types. Funnel events keep the names of
 * docs/in-progress/plans/admob-migration.md §3.6 (`ads_request` → `ads_loaded` /
 * `ads_load_failed` → `ads_show` → `ads_impression` → `ads_click` → `ads_dismiss`), and the
 * paid event becomes GA4's recommended `ad_impression` with revenue ([AdRevenue]) plus
 * `ads_paid` in micros for reconciling against the AdMob API.
 *
 * Every event carries `placement`, `ad_format` and the screen it happened on. Consent for
 * usage analytics is enforced by the [AnalyticsTracker]. Safe to call from any thread.
 */
interface AdsAnalytics {
    fun request(placement: AdPlacement)

    /** Load (or preload) succeeded after [latencyMs]; [adSource] = the network that filled it. */
    fun loaded(placement: AdPlacement, latencyMs: Long?, adSource: String?)

    /** Load failed: SDK error [code] name, its numeric value and the time it took. */
    fun loadFailed(placement: AdPlacement, code: String, codeNumber: Int? = null, latencyMs: Long? = null)

    /** Full-screen ad opened. */
    fun show(placement: AdPlacement)

    /** Full-screen ad could not be shown. */
    fun showFailed(placement: AdPlacement, code: String)

    /** Full-screen ad closed. */
    fun dismiss(placement: AdPlacement)
    fun impression(placement: AdPlacement)
    fun click(placement: AdPlacement)
    fun blocked(placement: AdPlacement, reason: String)

    /** Paid event (impression-level revenue). [adSource] = the network that served the ad. */
    fun paid(placement: AdPlacement, valueMicros: Long, currency: String, precision: String, adSource: String?)
    fun rewardEarned(placement: AdPlacement)
    fun consent(status: String, canRequestAds: Boolean, errorCode: Int?)
}

/** Event names and the revenue conversion, kept pure for unit tests. */
object AdRevenue {
    const val AD_PLATFORM = "AdMob"

    const val EVENT_REQUEST = "ads_request"
    const val EVENT_LOADED = "ads_loaded"
    const val EVENT_LOAD_FAILED = "ads_load_failed"
    const val EVENT_SHOW = "ads_show"
    const val EVENT_SHOW_FAILED = "ads_show_failed"
    const val EVENT_DISMISS = "ads_dismiss"
    const val EVENT_IMPRESSION = "ads_impression"
    const val EVENT_CLICK = "ads_click"
    const val EVENT_BLOCKED = "ads_blocked"
    const val EVENT_PAID = "ads_paid"
    const val EVENT_REWARD = "ads_rewarded_earned"
    const val EVENT_CONSENT = "consent_status"

    /** GA4 recommended event for ad revenue (Monetisation → Publisher ads). */
    const val EVENT_GA4_AD_IMPRESSION = "ad_impression"

    /** AdMob reports revenue in micros of [currency]: 1 500 000 → 1.5. */
    fun valueFromMicros(micros: Long): Double = micros / 1_000_000.0

    /** GA4 `ad_format` values: `banner`, `native`, `interstitial`, `app_open`, `rewarded`. */
    fun formatName(format: AdFormat): String = when (format) {
        AdFormat.AppOpen -> "app_open"
        AdFormat.Banner, AdFormat.CollapsibleBanner -> "banner"
        AdFormat.Native -> "native"
        AdFormat.Interstitial -> "interstitial"
        AdFormat.Rewarded -> "rewarded"
    }

    /** Parameters every ad event shares. */
    fun base(placement: AdPlacement, screen: String?): List<Pair<String, Any?>> = listOf(
        "placement" to placement.key,
        "ad_format" to formatName(placement.format),
        "screen" to screen,
    )

    /**
     * GA4 `ad_impression` from a paid event: `ad_platform`, `ad_source`, `ad_format`,
     * `ad_unit_name` (the placement key, which maps 1:1 to an ad unit), `value` in currency
     * units and `currency` (ISO 4217). A blank currency drops `value` too: GA4 rejects a
     * value without a currency. `precision` = `estimated` | `publisher_provided` | `precise`
     * | `unknown`.
     */
    fun ga4AdImpression(
        placement: AdPlacement,
        valueMicros: Long,
        currency: String,
        precision: String,
        adSource: String?,
        screen: String?,
    ): AnalyticsEvent {
        val hasCurrency = currency.isNotBlank()
        return AnalyticsEvent.of(
            EVENT_GA4_AD_IMPRESSION,
            "ad_platform" to AD_PLATFORM,
            "ad_source" to (adSource?.takeIf { it.isNotBlank() } ?: AD_PLATFORM),
            "ad_format" to formatName(placement.format),
            "ad_unit_name" to placement.key,
            "value" to if (hasCurrency) valueFromMicros(valueMicros) else null,
            "currency" to if (hasCurrency) currency.uppercase() else null,
            "precision" to precision.lowercase(),
            "placement" to placement.key,
            "screen" to screen,
        )
    }

    /** `ads_paid`: the raw micros, for reconciling with the AdMob API. */
    fun paidEvent(
        placement: AdPlacement,
        valueMicros: Long,
        currency: String,
        precision: String,
        screen: String?,
    ): AnalyticsEvent = AnalyticsEvent.of(
        EVENT_PAID,
        base(placement, screen) + listOf(
            "value_micros" to valueMicros,
            "currency" to currency.uppercase(),
            "precision" to precision.lowercase(),
        ),
    )
}

@Singleton
class FirebaseAdsAnalytics @Inject constructor(
    private val analytics: AnalyticsTracker,
    private val config: AdsConfig,
) : AdsAnalytics {
    private fun log(event: String, placement: AdPlacement?, vararg extra: Pair<String, Any?>) {
        val params = buildList {
            if (placement != null) addAll(AdRevenue.base(placement, analytics.currentScreen))
            addAll(extra)
        }
        analytics.log(AnalyticsEvent.of(event, params))
    }

    override fun request(placement: AdPlacement) = log(AdRevenue.EVENT_REQUEST, placement)

    override fun loaded(placement: AdPlacement, latencyMs: Long?, adSource: String?) =
        log(AdRevenue.EVENT_LOADED, placement, "latency_ms" to latencyMs, "ad_source" to adSource)

    override fun loadFailed(placement: AdPlacement, code: String, codeNumber: Int?, latencyMs: Long?) =
        log(
            AdRevenue.EVENT_LOAD_FAILED, placement,
            "code" to code, "code_num" to codeNumber, "latency_ms" to latencyMs,
        )

    override fun show(placement: AdPlacement) = log(AdRevenue.EVENT_SHOW, placement)

    override fun showFailed(placement: AdPlacement, code: String) =
        log(AdRevenue.EVENT_SHOW_FAILED, placement, "code" to code)

    override fun dismiss(placement: AdPlacement) = log(AdRevenue.EVENT_DISMISS, placement)
    override fun impression(placement: AdPlacement) = log(AdRevenue.EVENT_IMPRESSION, placement)
    override fun click(placement: AdPlacement) = log(AdRevenue.EVENT_CLICK, placement)

    /** Sampled at 10%: a cap can fire on every foreground. */
    override fun blocked(placement: AdPlacement, reason: String) {
        if (Random.nextInt(10) == 0) log(AdRevenue.EVENT_BLOCKED, placement, "reason" to reason)
    }

    override fun paid(
        placement: AdPlacement,
        valueMicros: Long,
        currency: String,
        precision: String,
        adSource: String?,
    ) {
        val screen = analytics.currentScreen
        analytics.log(AdRevenue.paidEvent(placement, valueMicros, currency, precision, screen))
        if (config.current().ga4AdImpression) {
            analytics.log(
                AdRevenue.ga4AdImpression(placement, valueMicros, currency, precision, adSource, screen)
            )
        }
    }

    override fun rewardEarned(placement: AdPlacement) = log(AdRevenue.EVENT_REWARD, placement)

    override fun consent(status: String, canRequestAds: Boolean, errorCode: Int?) =
        log(
            AdRevenue.EVENT_CONSENT, null,
            "status" to status.lowercase(), "can_request_ads" to canRequestAds,
            "error_code" to errorCode,
        )
}
