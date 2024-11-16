package tss.t.coreapi.models.databaseview

import androidx.room.Embedded
import androidx.room.Relation
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast

class PodcastAndEpisode(
    @Embedded
    val podcast: Podcast,
    @Relation(
        parentColumn = "id",
        entityColumn = "feedId",
    )
    val episode: List<Episode>
) {
}