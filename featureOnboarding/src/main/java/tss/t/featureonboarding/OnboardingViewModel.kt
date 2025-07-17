package tss.t.featureonboarding

import android.util.Log
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.core.storage.SharedPref
import tss.t.core.storage.hasSelectFavouriteCategory
import tss.t.core.storage.isOnboardingFinished
import tss.t.core.storage.saveFavouriteCategory
import tss.t.core.storage.saveOnboardingFinished
import tss.t.coreapi.models.CategoryRes
import tss.t.coreapi.models.TSDataState
import tss.t.podcasts.usecase.GetCategories
import tss.t.sharedfirebase.TSAnalytics
import tss.t.sharedresources.R
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val sharedPref: SharedPref,
    private val getCategories: GetCategories,
    val analytic: TSAnalytics
) : ViewModel() {
    private val _uiState by lazy {
        MutableStateFlow(
            OnboardingUIState(
                listItem = defaultItems,
                isSeparatedColor = true
            )
        )
    }

    private val _categoryState by lazy {
        MutableStateFlow<TSDataState<CategoryRes>>(TSDataState.Loading())
    }

    private val _listFavouriteCategory by lazy {
        MutableStateFlow<MutableList<CategoryRes.Category>>(mutableListOf())
    }

    val listFavouriteCategory: StateFlow<List<CategoryRes.Category>>
        get() = _listFavouriteCategory.asStateFlow()

    val categoryState: StateFlow<TSDataState<CategoryRes>>
        get() = _categoryState

    private val _onboardingStep = MutableStateFlow(
        OnboardingStep(
            isOnboardingDone = sharedPref.isOnboardingFinished(),
            isSelectedFavourite = sharedPref.hasSelectFavouriteCategory()
        )
    )
    val onboardingStep: StateFlow<OnboardingStep>
        get() = _onboardingStep

    val uiState: StateFlow<OnboardingUIState>
        get() = _uiState

    init {
        checkOnBoardingFinish()
        loadCategory()
    }

    private fun loadCategory() {
        viewModelScope.launch {
            _categoryState.update {
                TSDataState.Loading()
            }
            getCategories().collectLatest {
                _categoryState.value = it
            }
        }
    }

    private fun checkOnBoardingFinish() {
        _onboardingStep.update {
            it.copy(isOnboardingDone = sharedPref.isOnboardingFinished())
        }
    }

    fun onFinishOnboarding() {
        sharedPref.saveOnboardingFinished(true)
        analytic.trackEvent(
            "OnboardingFinished",
            "Onboarding"
        )
        _onboardingStep.update {
            it.copy(isOnboardingDone = sharedPref.isOnboardingFinished())
        }
    }

    fun onOnboardingDisposed() {

    }

    fun onPageChanged(page: Int) {
        analytic.trackScreen(
            "Onboarding",
            "CurrentPage" to (page + 1)
        )
    }

    fun onShowed() {
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun putFavouriteItem(category: CategoryRes.Category, index: Int) {
        _listFavouriteCategory.update {
            val newList = it
            val crrIndex = newList.indexOfFirst { it.id == category.id }
            if (crrIndex >= 0) {
                newList[crrIndex] = category.copy(isFavourite = true)
            } else {
                newList.add(category.copy(isFavourite = true))
            }
            newList
        }
        updateSelectedState(index, category)
    }

    var count = 0
    private fun updateSelectedState(index: Int, category: CategoryRes.Category) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_categoryState.value is TSDataState.Success) {
                val data = (_categoryState.value as TSDataState.Success).data
                val cached = data.feeds.toMutableList()
                cached[index] = category

                _categoryState.update {
                    count++
                    TSDataState.Success(CategoryRes(cached.size + count, cached))
                }
            }
        }
    }

    fun removeFavouriteItem(category: CategoryRes.Category, index: Int) {
        _listFavouriteCategory.update {
            val newList = it
            val crrIndex = newList.indexOfFirst { it.id == category.id }
            if (crrIndex > -1) {
                newList.removeAt(crrIndex)
            }
            newList
        }
        updateSelectedState(index, category)
    }

    fun saveFavouriteItem() {
        sharedPref.saveFavouriteCategory(_listFavouriteCategory.value.toSet())
        _onboardingStep.update {
            it.copy(isSelectedFavourite = true)
        }
    }

    data class OnboardingStep(
        val isOnboardingDone: Boolean = false,
        val isSelectedFavourite: Boolean = false
    )

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