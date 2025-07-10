package tss.t.coreradio.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import org.jsoup.nodes.Element
import tss.t.coreradio.api.RadioApi
import tss.t.coreradio.di.RadioRepo
import tss.t.coreradio.models.RadioChannel
import tss.t.coreradio.storage.dao.RadioChannelDao
import tss.t.sharedlibrary.utils.JsoupExt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VOHRepository @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val jsoupExt: JsoupExt,
    private val radioChannelDao: RadioChannelDao
) : RadioApi {
    private val baseUrl = BASE_URL

    override suspend fun getRadioList(): List<RadioChannel> {
        val main = jsoupExt.safeConnect(
            url = baseUrl,
            cookieReferer = baseUrl
        )?.body() ?: return radioChannelDao.getAllByCategory(RadioRepo.VOH)
        val moreChannelLink = main.selectFirst("a.btn-more-radio")
            ?.absUrl("href")
        var useMain = false
        val body = moreChannelLink?.let {
            runCatching {
                jsoupExt.connect(
                    url = it,
                    cookieReferer = baseUrl
                ).body()
            }.onFailure {
                useMain = true
            }.getOrNull()
        } ?: main
        val listChannel = if (useMain) {
            parseElementToDTO(body)
        } else {
            runCatching {
                parseNextDataToDTO(body)
            }.getOrElse {
                parseElementToDTOFromAllChannelPage(body)
            }
        }
        radioChannelDao.inserts(radioChannels = listChannel)
        return listChannel
    }

    private fun parseNextDataToDTO(body: Element): List<RadioChannel> {
        println("parseNextDataToDTO")
        val scripts = body.selectFirst("script#__NEXT_DATA__")
        println(scripts!!.html())
        val js = JSONObject(scripts.html())
        val channelJsArr = js.optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("pageData")
            ?.optJSONArray("blockList")
            ?.let {
                var obj = it.optJSONObject(0)
                for (index in 0 until it.length()) {
                    obj = it.optJSONObject(0)
                    if (obj?.optString("id") == "223" || obj?.optString("blockName") == "Kênh") {
                        break
                    }
                }
                obj
            }
            ?.optJSONArray("chanelList")
        if (channelJsArr == null || channelJsArr.length() == 0) {
            throw IllegalStateException("Empty data")
        }
        val listChannel = mutableListOf<RadioChannel>()
        for (index in 0 until channelJsArr.length()) {
            val jsObj = channelJsArr.optJSONObject(index) ?: continue
            val channel = RadioChannel(
                channelId = jsObj.optString("slug", ""),
                channelName = jsObj.optString("title", ""),
                links = listOf(
                    RadioChannel.Link(
                        RadioChannel.ItemLinkType.Playable,
                        link = jsObj.optString("url", ""),
                        id = jsObj.optString("url", ""),
                        source = RadioRepo.VOH
                    ),
                    RadioChannel.Link(
                        RadioChannel.ItemLinkType.Browsable,
                        link = jsObj.optString("slug", ""),
                        id = jsObj.optString("slug", ""),
                        source = RadioRepo.VOH
                    ),
                ),
                logo = jsObj.optString("logoPath", ""),
                categories = listOf(RadioRepo.VOH),
                category = RadioRepo.VOH
            )
            listChannel.add(channel)
        }
        return listChannel
    }

    private fun parseElementToDTO(body: Element): MutableList<RadioChannel> {
        val elements = body.select("div.radio-col")
        val listChannel = mutableListOf<RadioChannel>()
        for (radio in elements) {
            val title = radio.selectFirst("h3.radio-title a")?.attr("title").orEmpty()
            val channelLink = radio.selectFirst("h3.radio-title a")?.absUrl("href").orEmpty()
            val logo = radio.selectFirst("div.radio-thumb img")?.attr("src").orEmpty()
            val description =
                radio.selectFirst("div.radio-header h3.radio-title a")?.attr("title").orEmpty()

            listChannel.add(
                RadioChannel(
                    channelId = channelLink,
                    links = listOf(
                        RadioChannel.Link(
                            RadioChannel.ItemLinkType.Browsable,
                            link = channelLink,
                            id = channelLink,
                            source = RadioRepo.VOH
                        )
                    ),
                    logo = logo,
                    categories = listOf("VOH"),
                    channelName = title,
                    category = description
                )
            )
        }
        return listChannel
    }

    private fun parseElementToDTOFromAllChannelPage(body: Element): MutableList<RadioChannel> {
        val elements = body.select("div.radio-list-fm")
        val listChannel = mutableListOf<RadioChannel>()
        for (radio in elements) {
            val title = radio.selectFirst("h2.entry-titleFM a")?.text().orEmpty()
            val channelLink = radio.selectFirst("h2.entry-titleFM a")?.attr("abs:href").orEmpty()
            val logo = radio.selectFirst("div.radio-thumb img")?.attr("abs:src").orEmpty()
            val description = radio.selectFirst("div.entry-summary")?.text().orEmpty()

            listChannel.add(
                RadioChannel(
                    channelId = channelLink,
                    links = listOf(
                        RadioChannel.Link(
                            RadioChannel.ItemLinkType.Browsable,
                            link = channelLink,
                            id = channelLink,
                            source = RadioRepo.VOH
                        )
                    ),
                    logo = logo,
                    categories = listOf("VOH"),
                    channelName = title,
                    category = description
                )
            )
        }
        return listChannel
    }

    override suspend fun getPlayableLink(radioChannel: RadioChannel): RadioChannel.Link {
        return radioChannel.links.first { it.type == RadioChannel.ItemLinkType.Playable }
    }

    override suspend fun getPlayableLink(link: String): List<String> {
        return listOf(link)
    }

    companion object {
        private const val BASE_URL = "https://voh.com.vn/radio"
    }
}
