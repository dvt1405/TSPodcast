package tss.t.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import tss.t.core.storage.PodcastDatabase
import tss.t.core.storage.dao.FavouriteDao
import tss.t.coreapi.dao.EpisodeDao
import tss.t.coreapi.dao.FeedDao
import tss.t.coreapi.dao.PodcastDao
import tss.t.coreradio.storage.dao.RadioChannelDao

@Module
@InstallIn(SingletonComponent::class)
class StorageModule {

    @Provides
    fun provideDb(
        @ApplicationContext
        context: Context
    ): PodcastDatabase {
        return PodcastDatabase.instance(context)
    }

    @Provides
    fun providePodcastDao(
        podcastDatabase: PodcastDatabase
    ): PodcastDao = podcastDatabase.podcastDao()

    @Provides
    fun provideFeedDao(
        podcastDatabase: PodcastDatabase
    ): FeedDao = podcastDatabase.feedDao()

    @Provides
    fun provideEpisodeDao(
        podcastDatabase: PodcastDatabase
    ): EpisodeDao = podcastDatabase.episodeDao()


    @Provides
    fun favouriteDao(
        podcastDatabase: PodcastDatabase
    ): FavouriteDao = podcastDatabase.favouriteDao()


    @Provides
    fun radioDao(
        podcastDatabase: PodcastDatabase
    ): RadioChannelDao = podcastDatabase.radioDao()
}