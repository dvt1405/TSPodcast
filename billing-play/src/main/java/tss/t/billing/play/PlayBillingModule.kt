package tss.t.billing.play

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tss.t.billing.BillingController
import tss.t.billing.BillingImplementation

/**
 * Plugs Google Play Billing into the `:billing` seam. Being on the classpath is the switch:
 * the seam's unqualified BillingController resolves to this binding, and to the no-op one
 * when this module is absent.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayBillingModule {
    @Binds
    @BillingImplementation
    abstract fun bindController(impl: PlayBillingController): BillingController
}
