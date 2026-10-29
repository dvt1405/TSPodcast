package tss.t.ads.admob

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tss.t.ads.AdRenderer
import tss.t.ads.AdsController
import tss.t.ads.AdsImplementation

/**
 * Plugs AdMob into the `:ads` seam. Being on the classpath is the switch: the
 * seam's unqualified AdsController/AdRenderer resolve to these bindings, and to
 * the no-op objects when this module is absent.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AdMobModule {
    @Binds
    @AdsImplementation
    abstract fun bindController(impl: AdMobAdsController): AdsController

    @Binds
    @AdsImplementation
    abstract fun bindRenderer(impl: AdMobAdRenderer): AdRenderer
}
