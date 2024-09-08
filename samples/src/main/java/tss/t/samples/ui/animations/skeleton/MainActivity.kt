package tss.t.samples.ui.animations.skeleton

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tss.t.samples.ui.animations.skeleton.ui.theme.ComposeLibraryTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeLibraryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ElevatedButton(onClick = {
                            startActivity(Intent(this@MainActivity, PlaceHolderSample::class.java))
                        }) {
                            Text(text = "PullToRefresh")
                        }

                        ElevatedButton(onClick = {
                            startActivity(Intent(this@MainActivity, ShadowExamples::class.java))
                        }) {
                            Text(text = "ShadowSample")
                        }

                        ElevatedButton(onClick = {
                            startActivity(Intent(this@MainActivity, ButtonSamples::class.java))
                        }) {
                            Text(text = "Button")
                        }

                        ElevatedButton(onClick = {
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    BadgeActivitySamples::class.java
                                )
                            )
                        }) {
                            Text(text = "Badge")
                        }

                        ElevatedButton(onClick = {
                            startActivity(Intent(this@MainActivity, PopupSamples::class.java))
                        }) {
                            Text(text = "Popup")
                        }

                        ElevatedButton(onClick = {
                            startActivity(Intent(this@MainActivity, TextfieldSamples::class.java))
                        }) {
                            Text(text = "TextField")
                        }
                    }
                }
            }
        }
    }
}