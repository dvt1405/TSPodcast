package tss.t.samples.ui.animations.skeleton

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tss.t.samples.ui.animations.skeleton.ui.theme.ComposeLibraryTheme
import tss.t.sharedlibrary.ui.widget.TSTextField
import tss.t.sharedlibrary.ui.widget.TSMaterialTextField

class TextfieldSamples : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeLibraryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting2(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting2(name: String, modifier: Modifier = Modifier) {
    ComposeLibraryTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp)
                .verticalScroll(
                    rememberScrollState()
                ),
        ) {
            var value by remember {
                mutableStateOf("")
            }
            TSTextField(
                value = value,
                onValueChange = {
                    value = it
                },
                label = "Label",
                placeholder = "Hint text", helperText = "Helper text"
            )

            TSMaterialTextField(
                value,
                onValueChange = {
                    value = it
                },
                supportingText = "Test",
                label = "Label"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview4() {
    ComposeLibraryTheme {
        Greeting2("Android")
    }
}