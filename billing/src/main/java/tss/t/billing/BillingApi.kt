package tss.t.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-app purchase seam. Nothing in this module references a store SDK: `:app` talks only to
 * these types and `:billing-play` implements them. Contract:
 * docs/in-progress/plans/monetization-ads-iap.md.
 */

/** A configured [product] the store returned, with what the paywall shows. */
data class PaywallProduct(
    val product: IapProduct,
    /** Localised recurring (or one-time) price from the store, e.g. "₫49.000". */
    val formattedPrice: String,
    /** ISO 8601 billing period of the recurring price (`P1M`, `P1Y`); null for one-time. */
    val billingPeriod: String?,
    /** ISO 8601 free-trial length when the store offers the user an eligible trial, else null. */
    val freeTrialPeriod: String?,
)

sealed interface ProductsState {
    data object Loading : ProductsState

    /** [products] follow [IapProduct.order]; products the store did not return are left out. */
    data class Ready(val products: List<PaywallProduct>) : ProductsState

    /** Store not reachable / billing unavailable / nothing configured exists on the store. */
    data class Unavailable(val reason: String) : ProductsState
}

/** What the user owns according to the store (or the local cache until it answers). */
data class Entitlement(
    /** Owns a purchased product with the `ad_free` benefit. */
    val premium: Boolean,
    /** Play product id of the owned Premium subscription, for "Manage subscription"; else null. */
    val subscriptionStoreProductId: String? = null,
    /** Internal ids of purchases still pending (e.g. cash payment): shown, not granted. */
    val pendingProductIds: Set<String> = emptySet(),
    /** False until the store answered once this process (the value comes from the cache). */
    val verified: Boolean = false,
)

/** Outcome of [BillingController.launchPurchase], emitted on [BillingController.purchaseResults]. */
sealed interface PurchaseResult {
    /** Internal [IapProduct.id] of the product, when known. */
    val productId: String?

    data class Success(override val productId: String?) : PurchaseResult
    data class Pending(override val productId: String?) : PurchaseResult
    data class Cancelled(override val productId: String?) : PurchaseResult

    /** [code] = store response code name (`BILLING_UNAVAILABLE`, `NETWORK_ERROR`, …). */
    data class Failed(override val productId: String?, val code: String) : PurchaseResult
}

/** `source` values of the paywall analytics events. */
object PaywallSource {
    const val SETTINGS = "settings"

    /** A pending purchase completed outside the purchase flow (next start, Play callback). */
    const val DEFERRED = "deferred"
}

/** Outcome of [BillingController.restore]. */
sealed interface RestoreResult {
    /** [premium] false = the store has no ad-free purchase for this account. */
    data class Restored(val premium: Boolean) : RestoreResult
    data class Failed(val code: String) : RestoreResult
}

/** The only purchase abstraction `:app` is allowed to know. */
interface BillingController {
    /** Starts from the cached Premium flag; the store result overrides it. */
    val entitlement: StateFlow<Entitlement>

    /** `entitlement.premium` as its own flow (the ad layer only needs this). */
    val premium: StateFlow<Boolean>

    val products: StateFlow<ProductsState>

    val purchaseResults: SharedFlow<PurchaseResult>

    /**
     * Connects to the store and refreshes the purchases (restores Premium, acknowledges
     * anything not acknowledged yet). Idempotent; call from the host Activity's onCreate.
     * Implementations refresh again every time the app comes to the foreground.
     */
    fun start()

    /** Queries the store for the configured products (paywall). */
    fun loadProducts()

    /**
     * Opens the store's purchase sheet for internal [productId]. Returns false when it could
     * not be opened (product not loaded, store not ready); the result arrives on
     * [purchaseResults]. [source] = where the paywall was opened (analytics, [PaywallSource]).
     */
    fun launchPurchase(activity: Activity, productId: String, source: String): Boolean

    /** "Restore purchases": queries the store for owned subscriptions and one-time products. */
    suspend fun restore(source: String): RestoreResult
}

/** Default when no store implementation is on the classpath: a cached Premium still applies. */
class NoOpBillingController(cache: PremiumCache) : BillingController {
    override val entitlement: StateFlow<Entitlement> = MutableStateFlow(cache.entitlement()).asStateFlow()
    override val premium: StateFlow<Boolean> = MutableStateFlow(cache.premium).asStateFlow()
    override val products: StateFlow<ProductsState> =
        MutableStateFlow<ProductsState>(ProductsState.Unavailable("no_implementation")).asStateFlow()
    override val purchaseResults: SharedFlow<PurchaseResult> = MutableSharedFlow<PurchaseResult>().asSharedFlow()
    override fun start() = Unit
    override fun loadProducts() = Unit
    override fun launchPurchase(activity: Activity, productId: String, source: String) = false
    override suspend fun restore(source: String): RestoreResult = RestoreResult.Failed("no_implementation")
}
