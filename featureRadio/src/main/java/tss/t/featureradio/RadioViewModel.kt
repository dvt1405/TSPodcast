package tss.t.featureradio

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.coreapi.models.TSDataState
import tss.t.coreradio.models.RadioChannel
import tss.t.coreradio.usecase.GetPlayableLink
import tss.t.coreradio.usecase.GetRadioList
import tss.t.featureradio.models.RadioUISate
import javax.inject.Inject

@HiltViewModel
class RadioViewModel @Inject constructor(
    private val _getRadioList: GetRadioList,
    private val _getPlayableLink: GetPlayableLink,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState by lazy {
        MutableStateFlow<TSDataState<RadioUISate>>(TSDataState.Loading())
    }

    val uiState: StateFlow<TSDataState<RadioUISate>>
        get() = _uiState

    val listState by lazy {
        mutableStateOf(
            LazyListState(
                firstVisibleItemIndex = savedStateHandle[EXTRA_ITEM_INDEX] ?: 0,
                firstVisibleItemScrollOffset = savedStateHandle[EXTRA_ITEM_OFFSET] ?: 0
            )
        )
    }

    init {
        loadList()
    }

    fun loadList() {
        _uiState.update {
            TSDataState.Loading(
                data = it.data
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            _getRadioList().collectLatest {
                if (it.isSuccess) {
                    val list = it.getOrDefault(emptyList())
                    _uiState.update {
                        TSDataState.Success(
                            data = RadioUISate(list, false)
                        )
                    }
                } else {
                    _uiState.update {
                        TSDataState.Error(
                            data = it.data,
                            exception = it.exception()
                        )
                    }
                }

            }
        }
    }

    fun getPlayableLink(radioChannel: RadioChannel) {
        viewModelScope.launch(Dispatchers.IO) {
            _getPlayableLink(radioChannel).collectLatest {

            }
        }
    }

    override fun onCleared() {
        savedStateHandle[EXTRA_ITEM_INDEX] = listState.value.firstVisibleItemIndex
        savedStateHandle[EXTRA_ITEM_OFFSET] = listState.value.firstVisibleItemScrollOffset
        super.onCleared()
    }

    companion object {
        private const val EXTRA_ITEM_INDEX = "extra:item_index"
        private const val EXTRA_ITEM_OFFSET = "extra:item_offset"
    }
}