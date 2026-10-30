package tss.t.shared.playback

internal fun testItem(id: Int, isLive: Boolean = false) = PlayableItem(
    id = "$id",
    feedId = 42L,
    url = "https://example.com/$id.mp3",
    title = "Episode $id",
    podcastTitle = "Podcast",
    artworkUrl = null,
    referer = null,
    durationMs = 600_000L,
    isLive = isLive,
)
