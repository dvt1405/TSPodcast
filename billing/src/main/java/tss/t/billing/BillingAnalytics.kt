package tss.t.billing

import tss.t.sharedlibrary.analytics.AnalyticsEvent
import tss.t.sharedlibrary.analytics.AnalyticsTracker
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Paywall / purchase analytics without store types. Names and parameters are the contract's
 * (docs/in-progress/plans/monetization-ads-iap.md §3), shared with iOS: every event carries
 * `source` (where the paywall was opened, [PaywallSource]) and, for a product, `product_id`
 * (the internal [IapProduct.id], never the store id when it is known). Consent for usage
 * analytics is enforced by the [AnalyticsTracker]. Safe to call from any thread.
 */
interface BillingAnalytics {
    fun paywallView(source: String)
    fun purchaseStart(productId: String, source: String)
    fun purchaseSuccess(productId: String?, source: String)
    fun purchaseCancel(productId: String?, source: String)

    /** [code] = store response code name, e.g. `BILLING_UNAVAILABLE`, `pending`. */
    fun purchaseFail(productId: String?, source: String, code: String)

    /** [result] = `premium` (an ad-free purchase was found), `none` or `error` with [code]. */
    fun purchaseRestore(source: String, result: String, code: String? = null)
}

/** Event names and builders, kept pure for unit tests. */
object BillingEvents {
    const val PAYWALL_VIEW = "paywall_view"
    const val PURCHASE_START = "purchase_start"
    const val PURCHASE_SUCCESS = "purchase_success"
    const val PURCHASE_CANCEL = "purchase_cancel"
    const val PURCHASE_FAIL = "purchase_fail"
    const val PURCHASE_RESTORE = "purchase_restore"

    const val P_PRODUCT_ID = "product_id"
    const val P_SOURCE = "source"
    const val P_CODE = "code"
    const val P_RESULT = "result"

    const val RESULT_PREMIUM = "premium"
    const val RESULT_NONE = "none"
    const val RESULT_ERROR = "error"

    fun event(name: String, source: String, productId: String? = null, vararg extra: Pair<String, Any?>) =
        AnalyticsEvent.of(
            name,
            listOf(P_PRODUCT_ID to productId, P_SOURCE to source) + extra,
        )
}

@Singleton
class TrackerBillingAnalytics @Inject constructor(
    private val analytics: AnalyticsTracker,
) : BillingAnalytics {
    override fun paywallView(source: String) =
        analytics.log(BillingEvents.event(BillingEvents.PAYWALL_VIEW, source))

    override fun purchaseStart(productId: String, source: String) =
        analytics.log(BillingEvents.event(BillingEvents.PURCHASE_START, source, productId))

    override fun purchaseSuccess(productId: String?, source: String) =
        analytics.log(BillingEvents.event(BillingEvents.PURCHASE_SUCCESS, source, productId))

    override fun purchaseCancel(productId: String?, source: String) =
        analytics.log(BillingEvents.event(BillingEvents.PURCHASE_CANCEL, source, productId))

    override fun purchaseFail(productId: String?, source: String, code: String) =
        analytics.log(
            BillingEvents.event(BillingEvents.PURCHASE_FAIL, source, productId, BillingEvents.P_CODE to code)
        )

    override fun purchaseRestore(source: String, result: String, code: String?) =
        analytics.log(
            BillingEvents.event(
                BillingEvents.PURCHASE_RESTORE, source, null,
                BillingEvents.P_RESULT to result, BillingEvents.P_CODE to code,
            )
        )
}
