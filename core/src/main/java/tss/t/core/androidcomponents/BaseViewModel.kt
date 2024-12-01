package tss.t.core.androidcomponents

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

abstract class BaseViewModel() : ViewModel() {
    protected var renderCount: Int = 0

    private val _eventChannel by lazy {
        Channel<Event>(capacity = Channel.CONFLATED)
    }

    private val evenHandler by lazy {
        _eventChannel.consumeAsFlow()
            .map {
                ++renderCount
                handleEvent(it)
            }
            .filterNotNull()
    }

    init {
        evenHandler.launchIn(viewModelScope)
    }

    fun sendEvent(event: Event, vararg extras: Any) {
        viewModelScope.launch {
            _eventChannel.send(event)
        }
    }

    override fun onCleared() {
        super.onCleared()
        _eventChannel.close()
    }

    abstract fun handleEvent(event: Event)

    sealed class Event(val value: Any? = null) {
        data object Init : Event()
        data class Click(val id: Int) : Event(id)
        data class LongClick(val id: Int) : Event(id)
        data class Refresh(val id: Int = 0) : Event(id)
    }
}
