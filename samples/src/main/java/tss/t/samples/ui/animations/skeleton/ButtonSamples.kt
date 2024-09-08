package tss.t.samples.ui.animations.skeleton

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tss.t.samples.ui.animations.skeleton.ui.theme.ComposeLibraryTheme
import tss.t.sharedlibrary.ui.widget.TSButton
import tss.t.sharedlibrary.ui.widget.TSButtonDefaults
import tss.t.sharedlibrary.ui.widget.TSOutlineButton
import tss.t.sharedlibrary.ui.widget.TSOutlineRoundedButton
import tss.t.sharedlibrary.ui.widget.TSRoundedButton
import tss.t.sharedlibrary.ui.widget.TSSecondaryButton
import tss.t.sharedlibrary.ui.widget.TSSecondaryRoundedButton

class ButtonSamples : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeLibraryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TSButtonPreview()
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFFFFFFF
)
@Composable
fun TSButtonPreview() {
    ComposeLibraryTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.size(16.dp))
            TSButton(
                modifier = Modifier,
                "Android",
                onClick = {},
                contentPadding = TSButtonDefaults.CommonContentPadding
            )
            TSRoundedButton(
                modifier = Modifier,
                "Android",
                onClick = {},
            )
            TSRoundedButton(
                modifier = Modifier,
                "Android",
                onClick = {},
                rightIcon = Icons.Rounded.Favorite
            )
            TSButton(
                modifier = Modifier.fillMaxWidth(),
                "Android",
                onClick = {},
                leftIcon = Icons.Rounded.Favorite,
            )
            TSRoundedButton(
                modifier = Modifier.fillMaxWidth(),
                "Android",
                onClick = {},
                rightIcon = Icons.Rounded.Favorite,
            )
            TSRoundedButton(
                modifier = Modifier.fillMaxWidth(),
                "Android",
                onClick = {},
                leftIcon = Icons.Rounded.Favorite,
            )
            TSButton(
                modifier = Modifier.fillMaxWidth(),
                "Android",
                onClick = {},
                leftIcon = Icons.Rounded.Add,
                rightIcon = Icons.Rounded.Favorite,
            )
            TSRoundedButton(
                modifier = Modifier.fillMaxWidth(),
                "Android",
                onClick = {},
                leftIcon = Icons.Rounded.Add,
                rightIcon = Icons.Rounded.Favorite,
            )
            TSSecondaryRoundedButton(
                modifier = Modifier.fillMaxWidth(),
                "Cancel",
                onClick = {},
            )
            TSSecondaryButton(
                modifier = Modifier.fillMaxWidth(),
                title = "Secondary", onClick = { },
                borderSize = 0.dp
            )
            TSOutlineButton(
                modifier = Modifier.fillMaxWidth(),
                title = "AndroidOutline", onClick = { },
            )
            TSOutlineRoundedButton(
                modifier = Modifier.fillMaxWidth(),
                title = "AndroidOutline", onClick = { },
            )

        }
    }
}