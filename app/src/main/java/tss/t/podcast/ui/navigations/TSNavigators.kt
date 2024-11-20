package tss.t.podcast.ui.navigations

import androidx.media3.common.MediaItem
import tss.t.coreapi.models.Episode
import tss.t.coreapi.models.Podcast

sealed class TSNavigators(
    val id: String
) {
    data object MainNavigator : TSNavigators("MainNavigator")
    data object SearchNavigator : TSNavigators("Search")
    data class PodcastDetail(
        val podcast: Podcast,
        val playList: List<Episode> = emptyList()
    ) : TSNavigators("PodcastDetail")

    data class Player(
        val item: Episode,
        val playList: List<Episode> = emptyList(),
        val podcast: Podcast? = null
    ) : TSNavigators("Player") {
        override fun equals(other: Any?): Boolean {
            if (other !is Player) return false
            return podcast?.id == other.podcast?.id
                    && item.id == other.item.id
                    && playList.size == other.playList.size
        }

        override fun hashCode(): Int {
            return super.hashCode()
        }
    }

    data class PlayerFromMini(
        val item: MediaItem,
        val mediaId: String = item.mediaId
    ) : TSNavigators("PlayerFromMini")

    override fun equals(other: Any?): Boolean {
        if (other !is TSNavigators) return false
        return id == other.id
    }

    companion object {
        fun interface Observer {
            fun onChanged(route: TSNavigators?)
        }

        private val _observers by lazy {
            mutableListOf<Observer>()
        }

        private val _queue by lazy {
            ArrayDeque<TSNavigators>()
        }

        val isRoot: Boolean
            get() = _queue.isEmpty()

        fun addToRoute(
            route: TSNavigators
        ) {
            _queue.addFirst(route)
        }

        fun navigateTo(
            route: TSNavigators,
            addToStack: Boolean = true
        ) {
            if (addToStack) {
                _queue.add(route)
            }
            _observers.forEach {
                it.onChanged(route = route)
            }
        }

        fun popBack() {
            synchronized(_queue) {
                _queue.removeLastOrNull()
            }
            _queue.lastOrNull()
                .also { route ->
                    _observers.forEach {
                        it.onChanged(route = route)
                    }
                }
        }

        fun add(observer: Observer) {
            synchronized(_observers) {
                _observers.add(observer)
            }
        }

        fun remove(observer: Observer) {
            synchronized(_observers) {
                _observers.remove(observer)
            }
        }
    }
}