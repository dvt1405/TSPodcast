package tss.t.samples.ui.animations.skeleton

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tss.t.samples.ui.animations.skeleton.ui.theme.ComposeLibraryTheme
import tss.t.sharedlibrary.ui.shadow

class ShadowExamples : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeLibraryTheme {
                PreViewShadow()
            }
        }
    }

    @Composable
    @Preview
    private fun PreViewShadow() {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(top = 68.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shadow(
                            offsetX = 0.dp,
                            offsetY = (0).dp,
                            blurRadius = 40.dp
                        )
                        .background(Color.White)
                        .clickable {  }
                )

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shadow(
                            offsetX = 0.dp,
                            offsetY = (15).dp,
                            blurRadius = 20.dp
                        )
                        .background(Color.White)

                )

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shadow(
                            Color(0x1F000000),
                            offsetX = (-15).dp,
                            offsetY = (5).dp,
                            blurRadius = 20.dp
                        )
                        .background(Color.White)
                )

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shadow(
                            Color(0x1F000000),
                            offsetX = (-15).dp,
                            offsetY = (-15).dp
                        )
                        .background(Color.White)
                )

            }
        }
    }
}