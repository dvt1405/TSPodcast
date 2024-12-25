package tss.t.coreradio.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import tss.t.coreradio.RadioConstants
import tss.t.coreradio.api.RadioApi
import tss.t.coreradio.models.RadioChannel
import tss.t.coreradio.storage.dao.RadioChannelDao
import tss.t.sharedlibrary.utils.JsoupExt
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val jsoupExt: JsoupExt,
    private val radioChannelDao: RadioChannelDao
) : RadioApi {
    private val baseUrl by lazy {
        RadioConstants.VOV_URL
    }

    override suspend fun getRadioList(): List<RadioChannel> {
        val doc = jsoupExt.connect(baseUrl)
        val body = doc.body()
        val rows = body.select(".row .col")
        val listChannel = rows.mapNotNull {
            val link = it.getElementsByTag("a")
                .getOrNull(0)
                ?.attr("href")
                .takeIf {
                    !it.isNullOrEmpty()
                } ?: return@mapNotNull null
            val name = link.toHttpUrl()
                .pathSegments
                .lastOrNull()
                ?.split("-")
                ?.joinToString(" ") { word ->
                    if (word.contains("vov")) {
                        word.uppercase()
                    } else {
                        word.replaceFirstChar { ch -> ch.uppercase() }
                    }
                }
                ?: return@mapNotNull null
            val logo = it.getElementsByTag("source")
                .getOrNull(0)
                ?.attr("srcset")
                .takeIf {
                    !it.isNullOrEmpty()
                } ?: return@mapNotNull null

            RadioChannel(
                channelId = link,
                channelName = name,
                logo = logo,
                categories = listOf("VOV"),
                links = listOf(
                    RadioChannel.Link(
                        RadioChannel.ItemLinkType.Browsable,
                        link = link,
                        id = link,
                        source = baseUrl
                    )
                ),
                category = "VOV"
            )
        }
        radioChannelDao.inserts(listChannel)
        return listChannel
    }

    override suspend fun getPlayableLink(radioChannel: RadioChannel): RadioChannel.Link {
        val radioLink = radioChannel.links.first()
        val link = radioLink.link
        val listUrl = mutableListOf<String>()
        val body = jsoupExt.connect(url = link, cookieReferer = baseUrl)
        body.select("script").forEach {
            val regex = "(?<=url: \").*?(?=\")"
            val pattern = Pattern.compile(regex)
            val matcher = pattern.matcher(it.html())
            while (matcher.find()) {
                matcher.group(0)?.let { it1 -> listUrl.add(it1) }
            }
        }
        return radioLink.copy(
            link = listUrl.first(),
            type = RadioChannel.ItemLinkType.Playable
        )
    }

    override suspend fun getPlayableLink(link: String): List<String> {
        val listUrl = mutableListOf<String>()
        val body = jsoupExt.connect(url = link, cookieReferer = baseUrl)
        body.select("script").forEach {
            val regex = "(?<=url: \").*?(?=\")"
            val pattern = Pattern.compile(regex)
            val matcher = pattern.matcher(it.html())
            while (matcher.find()) {
                matcher.group(0)?.let { it1 -> listUrl.add(it1) }
            }
        }
        return listUrl
    }
}