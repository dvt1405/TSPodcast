package tss.t.podcast.ui.model

sealed interface HomeEvent {
    data object ToastDoubleClickToExit : HomeEvent
    data object ExitApp : HomeEvent
}