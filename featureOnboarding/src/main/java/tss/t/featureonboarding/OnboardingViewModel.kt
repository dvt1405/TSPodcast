package tss.t.featureonboarding

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import tss.t.core.storage.SharedPref
import tss.t.core.storage.isOnboardingFinished
import tss.t.core.storage.saveOnboardingFinished
import tss.t.sharedresources.R
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val sharedPref: SharedPref
) : ViewModel() {
    private val _uiState by lazy {
        MutableStateFlow(
            OnboardingUIState(
                listItem = defaultItems,
                isSeparatedColor = true
            )
        )
    }

    var isOnboardingFinished = MutableStateFlow(sharedPref.isOnboardingFinished())

    val uiState: StateFlow<OnboardingUIState>
        get() = _uiState

    init {
        checkOnBoardingFinish()
    }

    private fun checkOnBoardingFinish() {
        isOnboardingFinished.update {
            sharedPref.isOnboardingFinished()
        }
    }

    fun onFinishOnboarding() {
        sharedPref.saveOnboardingFinished(true)
        isOnboardingFinished.update {
            true
        }
    }

    fun onOnboardingDisposed() {

    }

    fun onPageChanged() {
    }

    fun onShowed() {
    }

    override fun onCleared() {
        super.onCleared()
    }

    data class OnboardingUIState(
        val listItem: List<PageData>,
        val isSeparatedColor: Boolean
    )

    data class PageData(
        val drawableRes: Int,
        val backgroundColor: Color,
        val brush: Brush? = null,
        val title: Int,
        val highlight: Int = 0
    )

    companion object {
        val defaultItems by lazy {
            listOf(
                PageData(
                    drawableRes = R.drawable.onboarding_slide_2,
                    backgroundColor = Color(0xFFEAF0E4),
                    brush = null,
                    R.string.onboarding_title1,
                    R.array.onboarding_title1_highlight
                ),
                PageData(
                    drawableRes = R.drawable.onboarding_slide_3,
                    backgroundColor = Color(0xFFC8EAEE),
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF88BCCF),
                            Color(0xFF88BCCF),
                            Color(0xFF88BCCF),
                            Color(0xFFC8EAEE),
                            Color(0xFFC8EAEE)
                        )
                    ),
                    R.string.onboarding_title2,
                    R.array.onboarding_title2_highlight
                ),
                PageData(
                    drawableRes = R.drawable.onboarding_slide_5,
                    backgroundColor = Color(0xFFE0F8F8),
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFFEF8C77),
                            Color(0xFFEF8C77),
                            Color(0xFFEF8C77),
                            Color(0xFFEE8D78),
                            Color(0xFFEE8D78),

                            )
                    ),
                    R.string.onboarding_title3,
                    R.array.onboarding_title3_highlight
                ),
                PageData(
                    drawableRes = R.drawable.onboarding_slide_6,
                    backgroundColor = Color(0xFFE0F8F8),
                    brush = null,
                    R.string.onboarding_title4,
                    R.array.onboarding_title4_highlight
                )
            )
        }
    }
}