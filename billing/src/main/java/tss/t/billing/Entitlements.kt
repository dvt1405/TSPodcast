package tss.t.billing

/**
 * Pure purchase rules, no store SDK: `:billing-play` maps Play's `Purchase` / `ProductDetails`
 * to these types and every rule here is unit tested (EntitlementsTest).
 */

/** Store state of an owned purchase. */
enum class OwnedState { Purchased, Pending, Unknown }

/** One store purchase as the rules need it. */
data class OwnedPurchase(
    /** Play product ids (a subscription's base plan is not part of a purchase). */
    val storeProductIds: List<String>,
    val state: OwnedState,
    val acknowledged: Boolean,
)

/** One pricing phase of a subscription offer. */
data class PricingPhaseInfo(
    val priceMicros: Long,
    val formattedPrice: String,
    /** ISO 8601. */
    val billingPeriod: String,
)

/** A subscription offer (base plan offer when [offerId] is null), as returned by the store. */
data class SubscriptionOfferInfo(
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val phases: List<PricingPhaseInfo>,
) {
    /** First free phase. Play only returns offers the user is eligible for. */
    val freeTrialPeriod: String? get() = phases.firstOrNull { it.priceMicros == 0L }?.billingPeriod

    /** The base plan's recurring price is the last phase. */
    val recurringPhase: PricingPhaseInfo? get() = phases.lastOrNull()
}

object Entitlements {

    /**
     * Play product ids that grant `ad_free`: the configured products plus the in-app defaults,
     * so a product removed from Remote Config later never takes away something already bought.
     */
    fun adFreeStoreProductIds(settings: IapSettings): Set<String> =
        (settings.products + IapSettings.DEFAULT_PRODUCTS)
            .filter { it.isAdFree }
            .mapTo(mutableSetOf()) { it.storeProductId }

    /**
     * Premium = any PURCHASED purchase (acknowledged, or being acknowledged by the caller) of a
     * product with the `ad_free` benefit. Pending purchases are reported, never granted.
     * [launchedProductId] disambiguates base plans of the same subscription for analytics/UI.
     */
    fun compute(
        purchases: List<OwnedPurchase>,
        settings: IapSettings,
        launchedProductId: String? = null,
    ): Entitlement {
        val adFreeIds = adFreeStoreProductIds(settings)
        val subscriptionIds = (settings.products + IapSettings.DEFAULT_PRODUCTS)
            .filter { it.type == IapProductType.Subscription }
            .mapTo(mutableSetOf()) { it.storeProductId }
        val owned = purchases.filter { it.state == OwnedState.Purchased }
            .flatMap { it.storeProductIds }
        val pending = purchases.filter { it.state == OwnedState.Pending }
            .flatMap { it.storeProductIds }
            .mapTo(mutableSetOf()) { internalId(it, settings, launchedProductId) }
        return Entitlement(
            premium = owned.any { it in adFreeIds },
            subscriptionStoreProductId = owned.firstOrNull { it in adFreeIds && it in subscriptionIds },
            pendingProductIds = pending,
            verified = true,
        )
    }

    /**
     * Internal id for a Play product id: [launchedProductId] when it is that product, else the
     * only configured product with that store id, else the store id itself (several base plans
     * of one subscription cannot be told apart from a purchase).
     */
    fun internalId(storeProductId: String, settings: IapSettings, launchedProductId: String? = null): String {
        val all = settings.products + IapSettings.DEFAULT_PRODUCTS.filter { d -> settings.products.none { it.id == d.id } }
        all.firstOrNull { it.id == launchedProductId && it.storeProductId == storeProductId }?.let { return it.id }
        val matches = all.filter { it.storeProductId == storeProductId }
        return matches.singleOrNull()?.id ?: storeProductId
    }

    /**
     * The offer to sell for [basePlanId]: an eligible free-trial offer when the store returned
     * one, else the plain base plan offer, else any offer of that base plan.
     */
    fun pickOffer(offers: List<SubscriptionOfferInfo>, basePlanId: String): SubscriptionOfferInfo? {
        val forPlan = offers.filter { it.basePlanId == basePlanId }
        return forPlan.firstOrNull { it.offerId != null && it.freeTrialPeriod != null }
            ?: forPlan.firstOrNull { it.offerId == null }
            ?: forPlan.firstOrNull()
    }
}
