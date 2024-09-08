package tss.t.podcast

import android.R.attr.apiKey
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tss.t.podcast.ui.theme.PodcastTheme
import tss.t.securedtoken.NativeLib
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.TimeZone


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PodcastTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        val calendar: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        val now: Date = Date()
        calendar.setTime(now)
        val secondsSinceEpoch: Long = calendar.getTimeInMillis() / 1000L
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.podcastindex.org/api/1.0/search/byterm?q=Test")
                val con = url.openConnection() as HttpURLConnection
                con.requestMethod = "GET"
                // not needed for now, maybe later so I keep it
                //con.setRequestProperty("Accept", "application/json");
                //con.setRequestProperty("Content-Type", "application/json; utf-8");
                val apiHeaderTime = NativeLib.getTime()
                Log.d("TuanDv", "onCreate apiHeaderTime: $apiHeaderTime")
                Log.d("TuanDv", "onCreate getAuthHeader: ${NativeLib.getAuthHeader()}")
                Log.d("TuanDv", "onCreate getApiKey: ${NativeLib.getApiKey()}")

                con.setRequestProperty("X-Auth-Date", apiHeaderTime)
                con.setRequestProperty("X-Auth-Key", NativeLib.getApiKey())
                con.setRequestProperty("Authorization", NativeLib.getAuthHeader())
                con.setRequestProperty("User-Agent", "SuperPodcastPlayer/1.8")
                con.doOutput = true
                con.setChunkedStreamingMode(0)
                val code = con.responseCode
                println("response code=$code")
                try {
                    BufferedReader(InputStreamReader(con.inputStream, "utf-8")).use { br ->
                        val response = StringBuilder()
                        var responseLine: String? = null
                        while ((br.readLine().also { responseLine = it }) != null) {
                            response.append(responseLine!!.trim { it <= ' ' })
                        }
                        println(response.toString())
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                con.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello ${NativeLib.getAuthHeader()}!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    PodcastTheme {
        Greeting("Android")
    }
}