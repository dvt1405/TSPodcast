package tss.t.coreapi.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class StatCurrent(
    @SerializedName("stats")
    val stats: Stats,
) : BaseResponse(), Parcelable {
    @Parcelize
    data class Stats(
        @SerializedName("episodeCountTotal")
        val episodeCountTotal: Int,
        @SerializedName("feedCountTotal")
        val feedCountTotal: Int,
        @SerializedName("feedsWithNewEpisodes10days")
        val feedsWithNewEpisodes10days: Int,
        @SerializedName("feedsWithNewEpisodes30days")
        val feedsWithNewEpisodes30days: Int,
        @SerializedName("feedsWithNewEpisodes3days")
        val feedsWithNewEpisodes3days: Int,
        @SerializedName("feedsWithNewEpisodes90days")
        val feedsWithNewEpisodes90days: Int,
        @SerializedName("feedsWithValueBlocks")
        val feedsWithValueBlocks: Int
    ) : Parcelable
}