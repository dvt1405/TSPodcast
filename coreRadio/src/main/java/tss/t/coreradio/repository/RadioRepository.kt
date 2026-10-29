package tss.t.coreradio.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import tss.t.coreradio.RadioConstants
import tss.t.coreradio.api.RadioApi
import tss.t.coreradio.di.RadioRepo
import tss.t.coreradio.models.RadioChannel
import tss.t.coreradio.storage.dao.RadioChannelDao
import tss.t.sharedlibrary.crash.Crash
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
        // vovmedia.vn no longer renders `.row .col` cards (checked 2026-10-29).
        // /radio now ships every channel as an Alpine.js `x-data` blob holding
        // JSON.parse('...') with the title, artwork and a direct HLS url.
        val doc = jsoupExt.safeConnect(RadioConstants.VOV_RADIO_URL, cookieReferer = baseUrl)
            ?: return radioChannelDao.getAllByCategory(RadioRepo.VOV)
        val seen = mutableSetOf<String>()
        val listChannel = doc.select("[x-data]").flatMap { element ->
            val data = element.attr("x-data")
                .replace("\\u0022", "\"")
                .replace("\\\\\\/", "/")
                .replace("\\/", "/")
            CHANNEL_REGEX.findAll(data).mapNotNull { match ->
                val (title, _, artwork, seed, stream) = match.destructured
                if (!stream.startsWith("https://") || !seen.add(seed)) return@mapNotNull null
                val page = RadioConstants.VOV_RADIO_URL + "/" + seed
                RadioChannel(
                    channelId = page,
                    channelName = title,
                    logo = artwork,
                    categories = listOf(RadioRepo.VOV),
                    links = listOf(
                        RadioChannel.Link(
                            RadioChannel.ItemLinkType.Playable,
                            link = stream,
                            id = page,
                            source = baseUrl
                        ),
                        RadioChannel.Link(
                            RadioChannel.ItemLinkType.Browsable,
                            link = page,
                            id = page,
                            source = baseUrl
                        )
                    ),
                    category = RadioRepo.VOV
                )
            }.toList()
        }
        if (listChannel.isEmpty()) {
            // The markup moved again: report it and keep whatever was cached.
            Crash.record(
                NoSuchElementException("VOV: no channel matched on /radio"),
                TAG,
                mapOf("radio_repo" to RadioRepo.VOV)
            )
            return radioChannelDao.getAllByCategory(RadioRepo.VOV)
        }
        radioChannelDao.inserts(listChannel)
        return listChannel
    }

    override suspend fun getPlayableLink(radioChannel: RadioChannel): RadioChannel.Link {
        radioChannel.links
            .firstOrNull { it.type == RadioChannel.ItemLinkType.Playable }
            ?.let { return it }
        val radioLink = radioChannel.links.firstOrNull()
            ?: throw IllegalStateException(
                "VOV: no link for channel ${radioChannel.channelId}"
            )
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
        // listUrl comes from a regex over inline <script> tags, so it is empty
        // whenever vovmedia.vn changes its markup. Reported rather than thrown
        // blind, so the parser breaking is visible in Crashlytics.
        val playable = listUrl.firstOrNull()
            ?: run {
                Crash.record(
                    NoSuchElementException("VOV: stream regex matched nothing"),
                    TAG,
                    mapOf(
                        "radio_repo" to RadioRepo.VOV,
                        "channel_id" to radioChannel.channelId
                    )
                )
                throw IllegalStateException("VOV: could not resolve a stream for $link")
            }
        return radioLink.copy(
            link = playable,
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

    companion object {
        private const val TAG = "RadioRepository"

        /** One live channel inside a decoded `x-data` blob on vovmedia.vn/radio. */
        private val CHANNEL_REGEX = Regex(
            "\"title\":\"([^\"]+)\",\"subtitle\":\"([^\"]*)\",\"label\":\"[^\"]*\"," +
                "\"artwork\":\"([^\"]*)\",\"seed\":\"([^\"]+)\",\"isLive\":true," +
                "\"sources\":\\[\\{\"url\":\"([^\"]+)\""
        )
    }
}
