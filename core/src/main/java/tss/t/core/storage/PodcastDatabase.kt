package tss.t.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import tss.t.core.models.FavouriteDTO
import tss.t.core.storage.converters.PodcastTypeConverters
import tss.t.core.storage.dao.FavouriteDao
import tss.t.coreapi.dao.EpisodeDao
import tss.t.coreapi.dao.FeedDao
import tss.t.coreapi.dao.PodcastDao
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Feed
import tss.t.coreapi.models.Podcast
import tss.t.coreradio.models.RadioChannel
import tss.t.coreradio.storage.dao.RadioChannelDao

@Database(
    entities = [
        Podcast::class,
        Feed::class,
        Episode::class,
        FavouriteDTO::class,
        RadioChannel::class
    ],
    version = PodcastDatabase.DB_VERSION,
    views = [],
    autoMigrations = [],
    exportSchema = true
)
@TypeConverters(PodcastTypeConverters::class)
abstract class PodcastDatabase : RoomDatabase() {

    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun feedDao(): FeedDao
    abstract fun favouriteDao(): FavouriteDao
    abstract fun radioDao(): RadioChannelDao

    companion object {
        @Volatile
        private var db: PodcastDatabase? = null
        const val DB_VERSION = 5

        @Synchronized
        fun instance(context: Context): PodcastDatabase {
            return db ?: Room.databaseBuilder(
                context,
                PodcastDatabase::class.java,
                "TSPodcastDatabase"
            )
                .addMigrations(object : Migration(1, 4) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE FavouriteDTO ADD COLUMN image TEXT")
                    }

                })
                .addMigrations(object : Migration(4, 5) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `RadioChannel` (`channelId` TEXT NOT NULL, `channelName` TEXT NOT NULL, `categories` TEXT NOT NULL, `category` TEXT NOT NULL, `logo` TEXT NOT NULL, `links` TEXT NOT NULL, PRIMARY KEY(`channelId`))")
                    }
                })
                .build()
                .also {
                    db = it
                }
        }
    }
}