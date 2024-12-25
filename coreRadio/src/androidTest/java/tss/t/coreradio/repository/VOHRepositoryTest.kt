package tss.t.coreradio.repository

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import tss.t.sharedlibrary.utils.JsoupExt

class VOHRepositoryTest {
    private lateinit var repository: VOHRepository
    private lateinit var context: Context
    private lateinit var jsoupExt: JsoupExt

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        jsoupExt = JsoupExt(context.applicationContext as Application)
        repository = VOHRepository(context, jsoupExt, radioChannelDao)
    }

    @Test
    fun testGetAllLink() = runTest {
        val list = repository.getRadioList()
        list.map {
            it
        }.forEach {
            println(Gson().toJson(it))
        }
        assert(list.isNotEmpty())
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