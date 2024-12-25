package tss.t.coreradio.repository

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import tss.t.coreradio.models.RadioChannel
import tss.t.coreradio.storage.dao.RadioChannelDao
import tss.t.sharedlibrary.utils.JsoupExt

class RadioRepositoryTest {
    private lateinit var repository: RadioRepository
    private lateinit var context: Context
    private lateinit var jsoupExt: JsoupExt

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        jsoupExt = JsoupExt(context.applicationContext as Application)
        repository = RadioRepository(
            context,
            jsoupExt,
            radioChannelDao
        )
    }

    @Test
    fun testGetAllLink() = runTest {
        val list = repository.getRadioList()
        list.map {
            it.links.first().link
        }.forEach {
            println(it)
        }
        assert(list.isNotEmpty())
        assert(radioChannelDao.getAll().size == list.size)
    }

    @Test
    fun testGetPlayableLink() {
        val list = runBlocking {
            repository.getRadioList()
        }
        assert(list.isNotEmpty())
        list.forEach { link ->
            val playableLink = runBlocking {
                repository.getPlayableLink(link)
            }
            println(playableLink.link)
            assert(playableLink.link.isNotEmpty())
        }
    }
}

internal val radioChannelDao by lazy {
    object : RadioChannelDao() {
        val set by lazy { mutableSetOf<RadioChannel>() }
        override suspend fun insert(radioChannel: RadioChannel) {
            println("insert: $radioChannel")
            set.add(radioChannel)
        }

        override suspend fun inserts(radioChannels: List<RadioChannel>) {
            println("insert: $radioChannels")
            set.addAll(radioChannels)
        }

        override suspend fun delete(favouriteDTO: RadioChannel) {
            println("delete: $favouriteDTO")
            set.remove(favouriteDTO)
        }

        override suspend fun delete(id: String) {
            println("delete: $id")
            set.removeIf { it.channelId == id }
        }

        override fun getAll(): List<RadioChannel> {
            return set.toList()
        }

    }
}