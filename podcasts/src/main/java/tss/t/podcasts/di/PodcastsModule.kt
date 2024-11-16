package tss.t.podcasts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tss.t.core.repository.IFavouriteRepository
import tss.t.core.repository.IHistoryRepository
import tss.t.core.repository.IPodcastRepository
import tss.t.podcasts.FavouriteRepository
import tss.t.podcasts.HistoryRepository
import tss.t.podcasts.LocalPodcastRepositoryImpl
import tss.t.podcasts.PodcastsRepositoryImpl
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
abstract class PodcastsModule {
    @Binds
    abstract fun podcastsRepository(impl: PodcastsRepositoryImpl): IPodcastRepository

    @Binds
    abstract fun favouriteRepository(impl: FavouriteRepository): IFavouriteRepository

    @Binds
    abstract fun historyRepository(impl: HistoryRepository): IHistoryRepository

    @Binds
    @Named(FOR_TEST)
    abstract fun testPodcastsRepository(impl: LocalPodcastRepositoryImpl): IPodcastRepository

    companion object {
        const val FOR_TEST = "ForTest"
    }
}