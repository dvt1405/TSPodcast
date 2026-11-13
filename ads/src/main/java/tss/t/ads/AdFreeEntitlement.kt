package tss.t.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the user owns an ad-free product (Premium, benefit `ad_free`), without knowing the
 * store. `:app` binds it to the billing layer with the [AdFreeSource] qualifier; without a
 * binding nobody is ever ad free.
 *
 * Premium switches every format off ([AdsState.Disabled] with reason [REASON_PREMIUM]): banner,
 * native, interstitial, App Open and rewarded, including the rewarded "ad free" entry
 * (docs/in-progress/plans/monetization-ads-iap.md §1.3). It is not the `ads_enabled` kill
 * switch. The flow must start from the locally cached entitlement, so a Premium user never
 * sees an ad flash at cold start before the store answers, and must update live after a
 * purchase, a restore or an expiry.
 */
interface AdFreeEntitlement {
    val isAdFree: StateFlow<Boolean>

    companion object {
        /** [AdsState.Disabled.reason] and `ads_blocked{reason}` while the user is Premium. */
        const val REASON_PREMIUM = "premium"
    }
}

/** Default when the app binds no [AdFreeSource]: ads follow Remote Config only. */
object NoAdFreeEntitlement : AdFreeEntitlement {
    override val isAdFree: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
}
