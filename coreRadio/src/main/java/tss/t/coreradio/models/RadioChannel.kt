package tss.t.coreradio.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "RadioChannel")
data class RadioChannel(
    @PrimaryKey
    //@SerializedName("channelId")
    @ColumnInfo("channelId")
    val channelId: String,
    //@SerializedName("channelName")
    @ColumnInfo("channelName")
    val channelName: String,
    //@SerializedName("categories")
    @ColumnInfo("categories")
    val categories: List<String>,
    //@SerializedName("category")
    @ColumnInfo("category")
    val category: String = "VOV",
    //@SerializedName("logo")
    @ColumnInfo("logo")
    val logo: String,
    //@SerializedName("links")
    @ColumnInfo("links")
    val links: List<Link>,
) {

    data class Link(
        //@SerializedName("type")
        val type: ItemLinkType,
        //@SerializedName("link")
        val link: String,
        //@SerializedName("id")
        val id: String,
        //@SerializedName("source")
        val source: String,
    )

    enum class ItemLinkType {
        Playable,
        Browsable
    }
}