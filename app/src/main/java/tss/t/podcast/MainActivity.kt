package tss.t.podcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import tss.t.featureonboarding.OnboardingScreen
import tss.t.featureonboarding.OnboardingViewModel
import tss.t.featureonboarding.SelectFavouriteCategoryScreen
import tss.t.podcast.ui.theme.PodcastTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val onboardingViewModel: OnboardingViewModel by viewModels<OnboardingViewModel>()

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PodcastTheme {
                val isOnboardingFinished by onboardingViewModel.isOnboardingFinished.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SharedTransitionLayout {
                        when {
                            !isOnboardingFinished.isOnboardingDone -> {
                                OnboardingScreen(
                                    modifier = Modifier,
                                    paddingValues = innerPadding,
                                    viewModel = onboardingViewModel
                                )
                            }

                            !isOnboardingFinished.isSelectedFavourite -> {
                                SelectFavouriteCategoryScreen(
                                    viewmodel = onboardingViewModel
                                )
                            }

                            else -> MainScreen(innerPadding)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun MainScreen(
        paddingValues: PaddingValues
    ) {

    }
}