package tss.t.samples.ui.animations.skeleton

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.core.androidcomponents.BaseViewModel
import tss.t.samples.ui.animations.skeleton.ui.theme.ComposeLibraryTheme
import tss.t.sharedlibrary.ui.widget.TSButton
import tss.t.sharedlibrary.ui.widget.TSButtonDefaults
import tss.t.sharedlibrary.ui.widget.TSOutlineButton
import tss.t.sharedlibrary.ui.widget.TSOutlineRoundedButton
import tss.t.sharedlibrary.ui.widget.TSRoundedButton
import tss.t.sharedlibrary.ui.widget.TSSecondaryButton
import tss.t.sharedlibrary.ui.widget.TSSecondaryRoundedButton

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
class ButtonSamples : ComponentActivity() {
    private val viewModel by lazy {
        ViewModelProvider.create(this)[ButtonViewModel::class]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeLibraryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TSButtonPreview(
                        onClick = {
                            viewModel.sendEvent(BaseViewModel.Event.Click(it))
                            viewModel.sendEvent(BaseViewModel.Event.LongClick(it))
                        }
                    )
                }
            }
        }
        lifecycleScope.launch {
            viewModel.uiState.collectLatest {
                Toast.makeText(
                    this@ButtonSamples,
                    it.message.plus(" ${it.renderCount}"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    }
}

class ButtonViewModel : BaseViewModel() {

    private val _uiState by lazy {
        MutableStateFlow(UIState("Init", renderCount = renderCount))
    }

    val uiState: StateFlow<UIState>
        get() = _uiState

    override fun handleEvent(event: Event) {
        when (event) {
            is Event.Init -> {
                _uiState.update { it.copy() }
            }

            is Event.Click -> {
                _uiState.update {
                    handleClick(event).copy(renderCount = renderCount) ?: it
                }
            }

            else -> {

            }
        }
    }

    private fun handleClick(event: Event.Click): UIState {
        return when (val id = event.id) {
            0 -> {
                UIState("HandleClick: $id")
            }

            1 -> {
                UIState("HandleClick: $id")
            }

            2 -> {
                UIState("HandleClick: $id")
            }

            else -> {
                UIState("UnHandleClick for id: $id")
            }
        }
    }

    data class UIState(val message: String, var renderCount: Int = 0)
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFFFFFFF
)
@Composable
fun TSButtonPreview(
    onClick: (Int) -> Unit = {}
) {
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
                onClick = { onClick(1) },
                contentPadding = TSButtonDefaults.CommonContentPadding
            )
            TSRoundedButton(
                modifier = Modifier,
                "Android",
                onClick = { onClick(2) },
            )
            TSRoundedButton(
                modifier = Modifier,
                "Android",
                onClick = { onClick(3) },
                rightIcon = Icons.Rounded.Favorite,

                )
            TSButton(
                modifier = Modifier.fillMaxWidth(),
                "Android",
                onClick = { onClick(4) },
                leftIcon = Icons.Rounded.Favorite,
            )
            TSRoundedButton(
                modifier = Modifier.fillMaxWidth(),
                "Android",
                onClick = { onClick(5) },
                rightIcon = Icons.Rounded.Favorite,
            )
            TSRoundedButton(
                modifier = Modifier.fillMaxWidth(),
                "Android",
                onClick = { onClick(6) },
                leftIcon = Icons.Rounded.Favorite,
                enable = false
            )
            TSButton(
                modifier = Modifier.fillMaxWidth(),
                "Android",
                onClick = { onClick(7) },
                leftIcon = Icons.Rounded.Add,
                rightIcon = Icons.Rounded.Favorite,
            )
            TSRoundedButton(
                modifier = Modifier.fillMaxWidth(),
                "Android",
                onClick = { onClick(8) },
                leftIcon = Icons.Rounded.Add,
                rightIcon = Icons.Rounded.Favorite,
            )
            TSSecondaryRoundedButton(
                modifier = Modifier.fillMaxWidth(),
                "Cancel",
                onClick = { onClick(9) },
            )
            TSSecondaryButton(
                modifier = Modifier.fillMaxWidth(),
                title = "Secondary",
                onClick = { onClick(10) },
                borderSize = 0.dp
            )
            TSOutlineButton(
                modifier = Modifier.fillMaxWidth(),
                title = "AndroidOutline",
                onClick = { onClick(11) },
            )
            TSOutlineRoundedButton(
                modifier = Modifier.fillMaxWidth(),
                title = "AndroidOutline",
                onClick = { onClick(12) },
            )

        }
    }
}