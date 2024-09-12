package tss.t.core.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharePrefTest {
    private lateinit var context: Context
    lateinit var sharedPref: SharedPref

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        sharedPref = SharedPref(context)
    }

    @Test
    fun testSaveBoolean() {
        sharedPref.save("TestBool", true)
        Assert.assertEquals(true, sharedPref.get<Boolean>("TestBool"))
    }

    @Test
    fun testSaveInt() {
        sharedPref.save("testSaveInt", 1)
        Assert.assertEquals(1, sharedPref.get<Int>("testSaveInt"))
    }

    @Test
    fun testSaveLong() {
        sharedPref.save("testSaveLong", 1L)
        Assert.assertEquals(1L, sharedPref.get<Long>("testSaveLong"))
    }

    @Test
    fun testSaveFloat() {
        sharedPref.save("testSaveFloat", 1f)
        Assert.assertEquals(1f, sharedPref.get<Float>("testSaveFloat"))
    }


    @Test
    fun testSaveString() {
        sharedPref.save("testSaveString", "1f")
        Assert.assertEquals("1f", sharedPref.get<String>("testSaveString"))
    }


    @Test
    fun testSaveLisString() {
        sharedPref.save("testSaveLisString", listOf("1f", "2f"))
        val listStr = sharedPref.get<List<String>>("testSaveLisString")
        Assert.assertEquals("1f", listStr!![0])
        Assert.assertEquals("2f", listStr[1])
    }

    @Test
    fun testSaveSetString() {
        sharedPref.save("testSaveSetString", setOf("1f", "2f"))
        val listStr = sharedPref.get<Set<String>>("testSaveSetString")
        Assert.assertEquals(true, listStr!!.contains("1f"))
        Assert.assertEquals(true, listStr.contains("2f"))
    }
}