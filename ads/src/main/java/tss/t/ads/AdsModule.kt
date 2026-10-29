package tss.t.ads

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
 * Qualifier for the real ad implementation. An implementation module (today
 * `:ads-admob`) binds `@AdsImplementation AdsController` and
 * `@AdsImplementation AdRenderer`; when no such module is on the classpath the
 * unqualified bindings below fall back to the no-op objects, so an ad-free
 * build only has to drop the dependency.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AdsImplementation

@Module
@InstallIn(SingletonComponent::class)
abstract class AdsSeamModule {
    @BindsOptionalOf
    @AdsImplementation
    abstract fun optionalController(): AdsController

    @BindsOptionalOf
    @AdsImplementation
    abstract fun optionalRenderer(): AdRenderer

    @Binds
    abstract fun bindAdsConfig(impl: RemoteAdsConfig): AdsConfig

    @Binds
    abstract fun bindCounterStore(impl: SharedPrefsAdCounterStore): AdCounterStore

    @Binds
    abstract fun bindAnalytics(impl: FirebaseAdsAnalytics): AdsAnalytics

    companion object {
        @Provides
        fun adsController(@AdsImplementation impl: Optional<AdsController>): AdsController =
            impl.orElse(NoOpAdsController)

        @Provides
        fun adRenderer(@AdsImplementation impl: Optional<AdRenderer>): AdRenderer =
            impl.orElse(NoOpAdRenderer)

        @Provides
        @Singleton
        fun adPolicy(store: AdCounterStore): AdPolicy = AdPolicy(store)
    }
}
