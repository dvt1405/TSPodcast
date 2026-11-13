package tss.t.billing

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the real store implementation. `:billing-play` binds
 * `@BillingImplementation BillingController`; without it the unqualified binding below falls
 * back to [NoOpBillingController], so a build without IAP only has to drop the dependency.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BillingImplementation

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingSeamModule {
    @BindsOptionalOf
    @BillingImplementation
    abstract fun optionalController(): BillingController

    @Binds
    abstract fun bindIapConfig(impl: RemoteIapConfig): IapConfig

    @Binds
    abstract fun bindAnalytics(impl: TrackerBillingAnalytics): BillingAnalytics

    companion object {
        @Provides
        @Singleton
        fun billingController(
            @BillingImplementation impl: Optional<BillingController>,
            cache: PremiumCache,
        ): BillingController = impl.orElseGet { NoOpBillingController(cache) }
    }
}
