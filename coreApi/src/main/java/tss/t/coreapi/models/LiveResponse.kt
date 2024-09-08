package tss.t.coreapi.models

data class LiveResponse(
    val count: Int,
    val items: List<Item>,
    val max: Int?,
) : BaseResponse() {
    data class Item(
        val categories: Categories?,
        val dateCrawled: Int,
        val datePublished: Int,
        val datePublishedPretty: String,
        val enclosureLength: Int,
        val enclosureType: String,
        val enclosureUrl: String,
        val explicit: Int,
        val feedId: Int,
        val feedImage: String,
        val feedItunesId: Int?,
        val feedLanguage: String,
        val feedTitle: String,
        val guid: String,
        val id: Int,
        val image: String,
        val link: String,
        val title: String
    )
}