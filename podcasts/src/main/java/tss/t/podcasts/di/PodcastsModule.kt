package tss.t.podcasts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tss.t.core.repository.IPodcastRepository
import tss.t.podcasts.PodcastsRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class PodcastsModule {
    @Binds
    abstract fun podcastsRepository(impl: PodcastsRepositoryImpl): IPodcastRepository
}