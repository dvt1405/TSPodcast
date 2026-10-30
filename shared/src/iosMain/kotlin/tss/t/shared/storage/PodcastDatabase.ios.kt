package tss.t.shared.storage

import androidx.room.Room

actual fun openPodcastDatabase(path: String): PodcastDatabase =
    Room.databaseBuilder<PodcastDatabase>(name = path).buildPodcastDatabase()

/** A database that lives in memory only (tests). */
fun inMemoryPodcastDatabase(): PodcastDatabase =
    Room.inMemoryDatabaseBuilder<PodcastDatabase>().buildPodcastDatabase()
