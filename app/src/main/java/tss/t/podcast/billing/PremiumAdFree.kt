package tss.t.podcast.billing

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.StateFlow
import tss.t.ads.AdFreeEntitlement
import tss.t.ads.AdFreeSource
import tss.t.billing.BillingController

/**
 * Premium (billing) → ad free (ads). `:app` is the only module that knows both seams, like
 * [tss.t.podcast.ads.AppAdsHostSignals]: `:ads` and `:billing` stay independent.
 */
class PremiumAdFreeEntitlement(billing: BillingController) : AdFreeEntitlement {
    override val isAdFree: StateFlow<Boolean> = billing.premium
}

@Module
@InstallIn(SingletonComponent::class)
object PremiumAdFreeModule {
    @Provides
    @AdFreeSource
    fun adFree(billing: BillingController): AdFreeEntitlement = PremiumAdFreeEntitlement(billing)
}
