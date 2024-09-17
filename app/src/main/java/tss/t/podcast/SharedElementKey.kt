package tss.t.podcast

import tss.t.coreapi.models.TrendingPodcast

data class SharedElementKey(
    val podcast: TrendingPodcast,
    val type: Type,
    val id: Any
) {
    enum class Type {
        Background,
        Image,
        Title,
        Description
    }

    override fun equals(other: Any?): Boolean {
        if (other !is SharedElementKey) return false
        if (other.id != id) return false
        if (other.podcast != podcast) return false
        return other.podcast == podcast
                && other.id == id
                && other.type == type
    }
}