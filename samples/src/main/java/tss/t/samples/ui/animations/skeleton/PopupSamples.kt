package tss.t.samples.ui.animations.skeleton

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import tss.t.samples.ui.animations.skeleton.ui.theme.ComposeLibraryTheme
import tss.t.sharedlibrary.ui.widget.TSButton
import tss.t.sharedlibrary.ui.widget.TSPopup

class PopupSamples : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeLibraryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PopupSample(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun PopupSample(name: String, modifier: Modifier = Modifier) {
    var showDialog by remember {
        mutableIntStateOf(1)
    }
    when (showDialog) {
        0 -> {
            Dialog(onDismissRequest = {
                showDialog = -1
            }) {
                TSPopup(
                    title = "Hi popup",
                    contentText = "Hello pop $name!",
                    positiveText = "Positive",
                    negativeText = "Negative",
                    onPositiveButtonClick = {
                    }
                )
            }
        }

        1 -> {
            Dialog(onDismissRequest = {
                showDialog = -1
            }) {
                TSPopup(
                    title = "Hi popup",
                    contentText = "Hello pop $name!",
                )
            }
        }

        2 -> {
            Dialog(onDismissRequest = {
                showDialog = -1
            }) {
                TSPopup(
                    contentText = "Hello pop $name!",
                )
            }
        }

        3 -> {
            Dialog(onDismissRequest = { showDialog = -1 }) {
                TSPopup(
                    title = "Hi popup",
                    contentText = "Hello pop $name!",
                    positiveText = "Positive",
                    onPositiveButtonClick = {
                        showDialog = 3
                    }
                )
            }
        }

        4 -> {
            Dialog(onDismissRequest = { showDialog = -1 }) {
                TSPopup(
                    title = "Hi popup",
                    contentText = "Hello pop $name!",
                    positiveText = "Positive",
                    negativeText = "Negative",
                    onPositiveButtonClick = {
                        showDialog = 4
                    },
                    onNegativeButtonClick = {
                        showDialog = 0
                    }
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        TSPopup(
            title = "Hi popup",
            contentText = "Hello pop $name!",
        )
        Spacer(modifier = Modifier.size(16.dp))
        TSButton(title = "Show Popup", onClick = { showDialog = 1 })
        Spacer(modifier = Modifier.size(16.dp))
        TSPopup(
            contentText = "Hello $name!",
        )
        Spacer(modifier = Modifier.size(16.dp))
        TSButton(title = "Show Popup", onClick = { showDialog = 2 })
        Spacer(modifier = Modifier.size(16.dp))
        TSPopup(
            title = "Hi popup",
            contentText = "Hello pop $name!",
            positiveText = "Positive",
            onPositiveButtonClick = {
                showDialog = 3
            }
        )
        Spacer(modifier = Modifier.size(16.dp))
        TSPopup(
            title = "Hi popup",
            contentText = "Hello pop $name!",
            positiveText = "Positive",
            negativeText = "Negative",
            onPositiveButtonClick = {
                showDialog = 4
            },
            onNegativeButtonClick = {
                showDialog = 0
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview3() {
    ComposeLibraryTheme {
        PopupSample("Android")
    }
}