package tss.t.coreapi.models

data class SearchByPersonRes(
    val count: Int,
    val description: String,
    val items: List<PodcastByPerson>,
    val query: String,
    val status: String
) {
}


data class PodcastByPerson(
    val chaptersUrl: String,
    val dateCrawled: Int,
    val datePublished: Int,
    val description: String,
    val duration: Int,
    val enclosureLength: Int,
    val enclosureType: String,
    val enclosureUrl: String,
    val episode: Int,
    val episodeType: String,
    val explicit: Int,
    val feedAuthor: String,
    val feedId: Int,
    val feedImage: String,
    val feedItunesId: Int,
    val feedLanguage: String,
    val feedTitle: String,
    val feedUrl: String,
    val guid: String,
    val id: Int,
    val image: String,
    val link: String,
    val season: Int,
    val title: String,
    val transcriptUrl: String,
    val transcripts: List<Transcript>
) {
    data class Transcript(
        val type: String,
        val url: String
    )
}