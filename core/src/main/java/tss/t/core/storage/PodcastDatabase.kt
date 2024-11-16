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

@Database(
    entities = [
        Podcast::class,
        Feed::class,
        Episode::class,
        FavouriteDTO::class
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

    companion object {
        @Volatile
        private var db: PodcastDatabase? = null
        const val DB_VERSION = 4

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
                .build()
                .also {
                    db = it
                }
        }
    }
}