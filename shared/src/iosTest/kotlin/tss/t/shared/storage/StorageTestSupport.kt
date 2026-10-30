package tss.t.shared.storage

import androidx.room.useReaderConnection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import tss.t.shared.model.Episode
import tss.t.shared.model.Podcast
import tss.t.shared.network.FixedClock

/** A fresh in-memory Room database and a fake clock; [close] after each test. */
class TestDatabase(startMillis: Long = DAY_MS * 30) {
    val clock = FixedClock(startMillis)
    val db: PodcastDatabase = inMemoryPodcastDatabase()
    val storage = PodcastStorage(db, clock)

    /** First column of every row of [sql], as text (raw read, no Room mapping). */
    suspend fun texts(sql: String): List<String?> = db.useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            buildList {
                while (statement.step()) add(if (statement.isNull(0)) null else statement.getText(0))
            }
        }
    }

    suspend fun count(table: String): Int = texts("SELECT COUNT(*) FROM $table").single()!!.toInt()

    fun close() = db.close()

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

fun episode(id: Long, feedId: Long = 42, published: Long = id * 1_000, explicit: Int = 0) = Episode(
    id = id,
    feedId = feedId,
    title = "Episode $id",
    enclosureUrl = "https://example.com/$id.mp3",
    datePublished = published,
    explicit = explicit,
)

fun podcast(id: Long, title: String = "Podcast $id") = Podcast(id = id, feedId = id, title = title, image = "https://example.com/$id.png")

/** Values of a Room flow, collected into a channel so a test can wait for each emission. */
suspend fun <T> Channel<T>.awaitValue(expected: T): T {
    while (true) {
        val value = receive()
        if (value == expected) return value
    }
}

/** For readability at call sites: `flow.into(channel)`. */
suspend fun <T> Flow<T>.into(channel: Channel<T>) = collect { channel.send(it) }
