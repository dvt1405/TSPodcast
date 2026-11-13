package tss.t.billing.play

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tss.t.billing.BillingAnalytics
import tss.t.billing.BillingController
import tss.t.billing.BillingEvents
import tss.t.billing.Entitlement
import tss.t.billing.Entitlements
import tss.t.billing.IapConfig
import tss.t.billing.IapProductType
import tss.t.billing.OwnedPurchase
import tss.t.billing.OwnedState
import tss.t.billing.PaywallProduct
import tss.t.billing.PaywallSource
import tss.t.billing.PremiumCache
import tss.t.billing.PricingPhaseInfo
import tss.t.billing.ProductsState
import tss.t.billing.PurchaseResult
import tss.t.billing.RestoreResult
import tss.t.billing.SubscriptionOfferInfo
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Google Play Billing (8.x) implementation of [BillingController].
 *
 * - Connection: auto service reconnection, plus a few retries with backoff for transient
 *   errors whenever an operation needs the service.
 * - Entitlement: `queryPurchasesAsync` for SUBS and INAPP every time the app comes to the
 *   foreground ([start] registers on ProcessLifecycleOwner) and on "Restore". Only a complete
 *   answer (both types OK) replaces the cached state, so a flaky connection never revokes
 *   Premium. Every PURCHASED, unacknowledged purchase is acknowledged (Play refunds after 3
 *   days otherwise); PENDING ones are reported, not granted.
 * - Purchase: [launchPurchase] → PurchasesUpdatedListener grants Premium at once, then the
 *   purchases are re-queried to reconcile.
 *
 * Threading: everything runs on the main thread (public methods, the Play listeners and
 * [scope]); the details map and the in-flight purchase are main-thread state.
 */
@Singleton
class PlayBillingController @Inject constructor(
    @ApplicationContext context: Context,
    private val config: IapConfig,
    private val cache: PremiumCache,
    private val analytics: BillingAnalytics,
) : BillingController, PurchasesUpdatedListener, DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val _entitlement = MutableStateFlow(cache.entitlement())
    override val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    private val _premium = MutableStateFlow(_entitlement.value.premium)
    override val premium: StateFlow<Boolean> = _premium.asStateFlow()

    private val _products = MutableStateFlow<ProductsState>(ProductsState.Loading)
    override val products: StateFlow<ProductsState> = _products.asStateFlow()

    private val _purchaseResults = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 8)
    override val purchaseResults: SharedFlow<PurchaseResult> = _purchaseResults.asSharedFlow()

    private val started = AtomicBoolean(false)
    private val connectMutex = Mutex()

    /** What the store returned for each internal product id, with the offer to buy. */
    private var details: Map<String, Pair<ProductDetails, String?>> = emptyMap()

    /** Internal id and source of the purchase flow on screen, if any. */
    private var inFlight: Pair<String, String>? = null

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        // onStart is replayed when the app is already in the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch { refreshPurchases() }
    }

    // ----- Connection -----

    private suspend fun connect(): BillingResult = connectMutex.withLock {
        var result = okResult()
        repeat(CONNECT_ATTEMPTS) { attempt ->
            if (client.isReady) return okResult()
            result = startConnection()
            if (result.responseCode == BillingResponseCode.OK) return result
            if (result.responseCode !in TRANSIENT) return result
            delay(RETRY_BASE_MS shl attempt)
        }
        result
    }

    private suspend fun startConnection(): BillingResult = suspendCancellableCoroutine { cont ->
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (cont.isActive) cont.resume(billingResult)
            }

            override fun onBillingServiceDisconnected() {
                // Auto reconnection takes over for later calls; fail this attempt only.
                if (cont.isActive) {
                    cont.resume(result(BillingResponseCode.SERVICE_DISCONNECTED))
                }
            }
        })
    }

    // ----- Purchases / entitlement -----

    private suspend fun refreshPurchases(): RestoreResult {
        val connection = connect()
        if (connection.responseCode != BillingResponseCode.OK) {
            log("refresh: not connected ${codeName(connection.responseCode)}")
            return RestoreResult.Failed(codeName(connection.responseCode))
        }
        val subs = client.queryPurchasesAsync(purchasesParams(ProductType.SUBS))
        val inApp = client.queryPurchasesAsync(purchasesParams(ProductType.INAPP))
        val failed = listOf(subs.billingResult, inApp.billingResult)
            .firstOrNull { it.responseCode != BillingResponseCode.OK }
        if (failed != null) {
            log("refresh: query failed ${codeName(failed.responseCode)}")
            return RestoreResult.Failed(codeName(failed.responseCode))
        }
        val purchases = subs.purchasesList + inApp.purchasesList
        purchases.forEach(::acknowledgeIfNeeded)
        val previous = _entitlement.value
        val next = Entitlements.compute(purchases.map(::owned), config.current(), inFlight?.first)
        // A pending purchase that went through while the app was closed or in the background.
        if (next.premium && !previous.premium && previous.pendingProductIds.isNotEmpty() && inFlight == null) {
            val productId = previous.pendingProductIds.first()
            analytics.purchaseSuccess(productId, PaywallSource.DEFERRED)
            _purchaseResults.tryEmit(PurchaseResult.Success(productId))
        }
        setEntitlement(next)
        log("refresh: ${purchases.size} purchases, premium=${next.premium} pending=${next.pendingProductIds}")
        return RestoreResult.Restored(next.premium)
    }

    private fun setEntitlement(entitlement: Entitlement) {
        _entitlement.value = entitlement
        _premium.value = entitlement.premium
        cache.save(entitlement)
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        scope.launch {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = client.acknowledgePurchase(params)
            // Not OK: retried on the next foreground refresh, well within Play's 3 days.
            log("acknowledge ${purchase.products}: ${codeName(result.responseCode)}")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        val flow = inFlight
        inFlight = null
        val productId = flow?.first
        val source = flow?.second ?: PaywallSource.DEFERRED
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                list.forEach(::acknowledgeIfNeeded)
                val settings = config.current()
                val update = Entitlements.compute(list.map(::owned), settings, productId)
                val current = _entitlement.value
                // Grant at once; the refresh below reconciles with everything the user owns.
                setEntitlement(
                    current.copy(
                        premium = current.premium || update.premium,
                        subscriptionStoreProductId = update.subscriptionStoreProductId
                            ?: current.subscriptionStoreProductId,
                        pendingProductIds = current.pendingProductIds + update.pendingProductIds,
                    )
                )
                list.forEach { purchase ->
                    val id = productId ?: purchase.products.firstOrNull()
                        ?.let { Entitlements.internalId(it, settings) }
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            analytics.purchaseSuccess(id, source)
                            _purchaseResults.tryEmit(PurchaseResult.Success(id))
                        }

                        Purchase.PurchaseState.PENDING -> {
                            analytics.purchaseFail(id, source, CODE_PENDING)
                            _purchaseResults.tryEmit(PurchaseResult.Pending(id))
                        }

                        else -> Unit
                    }
                }
                scope.launch { refreshPurchases() }
            }

            BillingResponseCode.USER_CANCELED -> if (flow != null) {
                analytics.purchaseCancel(productId, source)
                _purchaseResults.tryEmit(PurchaseResult.Cancelled(productId))
            }

            else -> {
                val code = codeName(billingResult.responseCode)
                log("purchase failed: $code ${billingResult.debugMessage}")
                if (billingResult.responseCode == BillingResponseCode.ITEM_ALREADY_OWNED) {
                    scope.launch { refreshPurchases() }
                }
                if (flow != null) {
                    analytics.purchaseFail(productId, source, code)
                    _purchaseResults.tryEmit(PurchaseResult.Failed(productId, code))
                }
            }
        }
    }

    // ----- Products -----

    override fun loadProducts() {
        scope.launch {
            if (_products.value !is ProductsState.Ready) _products.value = ProductsState.Loading
            val connection = connect()
            if (connection.responseCode != BillingResponseCode.OK) {
                _products.value = ProductsState.Unavailable(codeName(connection.responseCode))
                return@launch
            }
            val settings = config.current()
            val byType = settings.products.groupBy { it.type }
            val found = mutableMapOf<String, ProductDetails>()
            var lastError: Int? = null
            for ((type, products) in byType) {
                val playType = if (type == IapProductType.Subscription) ProductType.SUBS else ProductType.INAPP
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        products.map { it.storeProductId }.distinct().map { id ->
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(id)
                                .setProductType(playType)
                                .build()
                        }
                    )
                    .build()
                val result = client.queryProductDetails(params)
                if (result.billingResult.responseCode != BillingResponseCode.OK) {
                    lastError = result.billingResult.responseCode
                    continue
                }
                result.productDetailsList.orEmpty().forEach { found["$playType/${it.productId}"] = it }
            }

            val newDetails = mutableMapOf<String, Pair<ProductDetails, String?>>()
            val paywall = settings.products.mapNotNull { product ->
                when (product.type) {
                    IapProductType.Subscription -> {
                        val pd = found["${ProductType.SUBS}/${product.storeProductId}"] ?: return@mapNotNull null
                        val offers = pd.subscriptionOfferDetails.orEmpty().map { offer ->
                            SubscriptionOfferInfo(
                                basePlanId = offer.basePlanId,
                                offerId = offer.offerId,
                                offerToken = offer.offerToken,
                                phases = offer.pricingPhases.pricingPhaseList.map { phase ->
                                    PricingPhaseInfo(phase.priceAmountMicros, phase.formattedPrice, phase.billingPeriod)
                                },
                            )
                        }
                        val offer = Entitlements.pickOffer(offers, product.basePlanId.orEmpty())
                            ?: return@mapNotNull null
                        val recurring = offer.recurringPhase ?: return@mapNotNull null
                        newDetails[product.id] = pd to offer.offerToken
                        PaywallProduct(product, recurring.formattedPrice, recurring.billingPeriod, offer.freeTrialPeriod)
                    }

                    IapProductType.OneTime -> {
                        val pd = found["${ProductType.INAPP}/${product.storeProductId}"] ?: return@mapNotNull null
                        val offer = pd.oneTimePurchaseOfferDetails ?: return@mapNotNull null
                        newDetails[product.id] = pd to offer.offerToken?.takeIf { it.isNotEmpty() }
                        PaywallProduct(product, offer.formattedPrice, null, null)
                    }
                }
            }
            details = newDetails
            _products.value = when {
                paywall.isNotEmpty() -> ProductsState.Ready(paywall)
                lastError != null -> ProductsState.Unavailable(codeName(lastError))
                else -> ProductsState.Unavailable(UNAVAILABLE_NOT_FOUND)
            }
            log("products: ${paywall.map { it.product.id }} (error=${lastError?.let(::codeName)})")
        }
    }

    override fun launchPurchase(activity: Activity, productId: String, source: String): Boolean {
        val (pd, offerToken) = details[productId] ?: return false
        if (!client.isReady) return false
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(pd)
            .apply { if (offerToken != null) setOfferToken(offerToken) }
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        analytics.purchaseStart(productId, source)
        inFlight = productId to source
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingResponseCode.OK) {
            // The sheet did not open (the caller shows it); the listener may not be called.
            if (inFlight?.first == productId) inFlight = null
            analytics.purchaseFail(productId, source, codeName(result.responseCode))
            return false
        }
        return true
    }

    override suspend fun restore(source: String): RestoreResult {
        val result = refreshPurchases()
        when (result) {
            is RestoreResult.Restored -> analytics.purchaseRestore(
                source, if (result.premium) BillingEvents.RESULT_PREMIUM else BillingEvents.RESULT_NONE,
            )

            is RestoreResult.Failed -> analytics.purchaseRestore(source, BillingEvents.RESULT_ERROR, result.code)
        }
        return result
    }

    // ----- Helpers -----

    private fun owned(purchase: Purchase) = OwnedPurchase(
        storeProductIds = purchase.products,
        state = when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> OwnedState.Purchased
            Purchase.PurchaseState.PENDING -> OwnedState.Pending
            else -> OwnedState.Unknown
        },
        acknowledged = purchase.isAcknowledged,
    )

    private fun purchasesParams(type: String) =
        QueryPurchasesParams.newBuilder().setProductType(type).build()

    private fun result(code: Int) = BillingResult.newBuilder().setResponseCode(code).build()
    private fun okResult() = result(BillingResponseCode.OK)

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "TSBilling"
        const val CONNECT_ATTEMPTS = 3
        const val RETRY_BASE_MS = 1_000L
        const val CODE_PENDING = "pending"
        const val UNAVAILABLE_NOT_FOUND = "not_found"

        val TRANSIENT = setOf(
            BillingResponseCode.SERVICE_DISCONNECTED,
            BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.NETWORK_ERROR,
            BillingResponseCode.ERROR,
        )

        fun codeName(code: Int): String = when (code) {
            BillingResponseCode.OK -> "OK"
            BillingResponseCode.USER_CANCELED -> "USER_CANCELED"
            BillingResponseCode.SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
            BillingResponseCode.BILLING_UNAVAILABLE -> "BILLING_UNAVAILABLE"
            BillingResponseCode.ITEM_UNAVAILABLE -> "ITEM_UNAVAILABLE"
            BillingResponseCode.DEVELOPER_ERROR -> "DEVELOPER_ERROR"
            BillingResponseCode.ERROR -> "ERROR"
            BillingResponseCode.ITEM_ALREADY_OWNED -> "ITEM_ALREADY_OWNED"
            BillingResponseCode.ITEM_NOT_OWNED -> "ITEM_NOT_OWNED"
            BillingResponseCode.SERVICE_DISCONNECTED -> "SERVICE_DISCONNECTED"
            BillingResponseCode.FEATURE_NOT_SUPPORTED -> "FEATURE_NOT_SUPPORTED"
            BillingResponseCode.NETWORK_ERROR -> "NETWORK_ERROR"
            else -> "CODE_$code"
        }
    }
}
