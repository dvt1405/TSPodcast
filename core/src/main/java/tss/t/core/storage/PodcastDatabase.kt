package tss.t.core.storage

import android.content.Context
import androidx.room.BuiltInTypeConverters
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import tss.t.core.models.FavouriteDTO
import tss.t.core.models.ListeningHistory
import tss.t.core.storage.converters.PodcastTypeConverters
import tss.t.core.storage.dao.FavouriteDao
import tss.t.core.storage.dao.ListeningHistoryDao
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
        RadioChannel::class,
        ListeningHistory::class,
    ],
    version = PodcastDatabase.DB_VERSION,
    views = [],
    autoMigrations = [],
    exportSchema = true
)
@TypeConverters(
    PodcastTypeConverters::class,
    builtInTypeConverters = BuiltInTypeConverters(
        enums = BuiltInTypeConverters.State.ENABLED
    )
)
abstract class PodcastDatabase : RoomDatabase() {

    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun feedDao(): FeedDao
    abstract fun favouriteDao(): FavouriteDao
    abstract fun radioDao(): RadioChannelDao
    abstract fun listeningHistoryDao(): ListeningHistoryDao

    companion object {
        val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE FavouriteDTO ADD COLUMN image TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `RadioChannel` (`channelId` TEXT NOT NULL, `channelName` TEXT NOT NULL, `categories` TEXT NOT NULL, `category` TEXT NOT NULL, `logo` TEXT NOT NULL, `links` TEXT NOT NULL, PRIMARY KEY(`channelId`))")
            }
        }

        // cachedAt lets API-derived rows expire. PodcastIndex's
        // terms forbid keeping cached copies longer than the cache
        // header allows, and it sends no-cache, must-revalidate.
        // Existing rows default to 0 so they expire on first prune.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Podcast ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Feed ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Episode ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Local listening history ("Lịch sử", "Nghe tiếp"), see [ListeningHistory]. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `ListeningHistory` (`mediaId` TEXT NOT NULL, `feedId` INTEGER, `title` TEXT NOT NULL, `podcastTitle` TEXT, `image` TEXT, `mediaUrl` TEXT, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `finished` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ListeningHistory_lastPlayedAt` ON `ListeningHistory` (`lastPlayedAt`)")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        @Volatile
        private var db: PodcastDatabase? = null
        const val DB_VERSION = 7

        @Synchronized
        fun instance(context: Context): PodcastDatabase {
            return db ?: Room.databaseBuilder(
                context,
                PodcastDatabase::class.java,
                "TSPodcastDatabase"
            )
                .addMigrations(*MIGRATIONS)
                .build()
                .also {
                    db = it
                }
        }
    }
}