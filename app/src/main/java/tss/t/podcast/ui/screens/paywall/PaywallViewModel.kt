package tss.t.podcast.ui.screens.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tss.t.billing.BillingAnalytics
import tss.t.billing.BillingController
import tss.t.billing.Entitlement
import tss.t.billing.IapConfig
import tss.t.billing.PaywallProduct
import tss.t.billing.ProductsState
import tss.t.billing.PurchaseResult
import tss.t.billing.RestoreResult
import javax.inject.Inject

/** One-off feedback shown as a snackbar. */
enum class PaywallMessage { Restored, NothingToRestore, RestoreFailed, PurchaseFailed, Pending, Unavailable }

/**
 * Paywall (docs/in-progress/plans/monetization-ads-iap.md §3). Products, prices and trials come
 * from the store through [BillingController]; the preselected plan is `iap_default_product`.
 * Purchase outcome analytics are logged by the controller, `paywall_view` here.
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingController,
    private val iapConfig: IapConfig,
    private val analytics: BillingAnalytics,
) : ViewModel() {

    val products: StateFlow<ProductsState> = billing.products
    val entitlement: StateFlow<Entitlement> = billing.entitlement

    private val _selected = MutableStateFlow<String?>(null)

    /** Internal id the user picked, or null = [preselect]. */
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    private val _messages = MutableSharedFlow<PaywallMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<PaywallMessage> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            billing.purchaseResults.collect { result ->
                when (result) {
                    is PurchaseResult.Pending -> _messages.tryEmit(PaywallMessage.Pending)
                    is PurchaseResult.Failed -> _messages.tryEmit(
                        // Play already has it: the controller re-queries and Premium turns on.
                        if (result.code == ITEM_ALREADY_OWNED) PaywallMessage.Restored else PaywallMessage.PurchaseFailed
                    )
                    // Success switches the screen to "Premium active"; cancel needs no message.
                    is PurchaseResult.Success, is PurchaseResult.Cancelled -> Unit
                }
            }
        }
    }

    /** The paywall became visible (each time it is opened). */
    fun onShown(source: String) {
        analytics.paywallView(source)
        billing.loadProducts()
    }

    fun retry() = billing.loadProducts()

    fun select(productId: String) {
        _selected.value = productId
    }

    /** The plan to highlight: the user's pick, else `iap_default_product`, else the first one. */
    fun preselect(available: List<PaywallProduct>, picked: String?): PaywallProduct? =
        available.firstOrNull { it.product.id == picked }
            ?: available.firstOrNull { it.product.id == iapConfig.current().defaultProductId }
            ?: available.firstOrNull()

    fun buy(activity: Activity, product: PaywallProduct, source: String) {
        if (!billing.launchPurchase(activity, product.product.id, source)) {
            _messages.tryEmit(PaywallMessage.Unavailable)
        }
    }

    fun restore(source: String) {
        if (_restoring.value) return
        viewModelScope.launch {
            _restoring.value = true
            val message = when (val result = billing.restore(source)) {
                is RestoreResult.Restored ->
                    if (result.premium) PaywallMessage.Restored else PaywallMessage.NothingToRestore

                is RestoreResult.Failed -> PaywallMessage.RestoreFailed
            }
            _restoring.value = false
            _messages.tryEmit(message)
        }
    }

    private companion object {
        const val ITEM_ALREADY_OWNED = "ITEM_ALREADY_OWNED"
    }
}
