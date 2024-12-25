package tss.t.coreradio.api

import tss.t.coreradio.models.RadioChannel

interface RadioApi {
    suspend fun getRadioList(): List<RadioChannel>
    suspend fun getPlayableLink(radioChannel: RadioChannel): RadioChannel.Link
    suspend fun getPlayableLink(link: String): List<String>
}