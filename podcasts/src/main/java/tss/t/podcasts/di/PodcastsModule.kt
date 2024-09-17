package tss.t.podcasts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import tss.t.core.repository.IPodcastRepository
import tss.t.podcasts.LocalPodcastRepositoryImpl
import tss.t.podcasts.PodcastsRepositoryImpl
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
abstract class PodcastsModule {
    @Binds
    abstract fun podcastsRepository(impl: PodcastsRepositoryImpl): IPodcastRepository

    @Binds
    @Named(FOR_TEST)
    abstract fun testPodcastsRepository(impl: LocalPodcastRepositoryImpl): IPodcastRepository

    companion object {
        const val FOR_TEST = "ForTest"
    }
}