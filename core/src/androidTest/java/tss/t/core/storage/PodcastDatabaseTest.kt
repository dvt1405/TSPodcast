package tss.t.core.storage

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tss.t.core.models.ListeningHistory

@RunWith(AndroidJUnit4::class)
class PodcastDatabaseTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PodcastDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate6To7AddsTheListeningHistoryAndKeepsFavourites() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO FavouriteDTO (id, type, title, image, description) " +
                    "VALUES ('42', 'PodcastEpisode', 'Fav', NULL, NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, PodcastDatabase.MIGRATION_6_7)

        db.query("SELECT title FROM FavouriteDTO WHERE id = '42'").use {
            it.moveToFirst()
            assertEquals("Fav", it.getString(0))
        }
        db.execSQL(
            "INSERT INTO ListeningHistory (mediaId, feedId, title, podcastTitle, image, mediaUrl, " +
                "positionMs, durationMs, finished, lastPlayedAt) " +
                "VALUES ('42', 7, 'Episode', 'Show', NULL, 'https://e/42.mp3', 300000, 3600000, 0, 1)"
        )
        db.query("SELECT positionMs FROM ListeningHistory WHERE mediaId = '42'").use {
            it.moveToFirst()
            assertEquals(300_000L, it.getLong(0))
        }
    }

    @Test
    fun migrateFromVersion5ToTheLatest() {
        helper.createDatabase(TEST_DB, 5).close()
        helper.runMigrationsAndValidate(
            TEST_DB,
            PodcastDatabase.DB_VERSION,
            true,
            *PodcastDatabase.MIGRATIONS
        )
    }

    @Test
    fun historyDaoKeepsTheNewestRowsAndFindsTheOneToResume() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, PodcastDatabase::class.java).build()
        try {
            val dao = database.listeningHistoryDao()
            for (i in 1..5) {
                dao.upsert(row("$i", position = i * 60_000L, finished = i == 5, at = i.toLong()))
            }
            // Newest unfinished one past the first seconds: 4 (5 is heard).
            assertEquals("4", dao.latestUnfinished(5_000L)?.mediaId)
            assertEquals(listOf("4", "3", "2", "1"), dao.observeUnfinished(5_000L, 10).first().map { it.mediaId })

            assertEquals(2, dao.prune(keep = 3))
            assertEquals(listOf("5", "4", "3"), dao.observeAll().first().map { it.mediaId })

            dao.delete("4")
            assertNull(dao.selectById("4"))
            assertNotNull(dao.selectById("3"))

            dao.clear()
            assertFalse(dao.observeAll().first().isNotEmpty())
        } finally {
            database.close()
        }
    }

    private fun row(id: String, position: Long, finished: Boolean, at: Long) = ListeningHistory(
        mediaId = id,
        feedId = 1L,
        title = "Episode $id",
        podcastTitle = "Show",
        image = null,
        mediaUrl = "https://e/$id.mp3",
        positionMs = position,
        durationMs = 3_600_000L,
        finished = finished,
        lastPlayedAt = at,
    )

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
