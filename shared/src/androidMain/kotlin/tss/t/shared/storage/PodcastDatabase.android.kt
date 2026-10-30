package tss.t.shared.storage

/**
 * Android keeps its own database (`:core` `PodcastDatabase` v6) and does not use `shared` yet
 * (owner decision); building Room here would also need a `Context`.
 */
actual fun openPodcastDatabase(path: String): PodcastDatabase =
    throw UnsupportedOperationException("Android uses :core PodcastDatabase, not the shared database")
