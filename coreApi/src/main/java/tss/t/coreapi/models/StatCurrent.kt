package tss.t.coreapi.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class StatCurrent(
    val stats: Stats,
) : BaseResponse(), Parcelable {
    @Parcelize
    data class Stats(
        val episodeCountTotal: Int,
        val feedCountTotal: Int,
        val feedsWithNewEpisodes10days: Int,
        val feedsWithNewEpisodes30days: Int,
        val feedsWithNewEpisodes3days: Int,
        val feedsWithNewEpisodes90days: Int,
        val feedsWithValueBlocks: Int
    ) : Parcelable
}