package tss.t.sharedplayer.playback

/**
 * What the sleep timer is set to. Same model as iOS (`shared/.../playback/SleepTimer.kt`, M6):
 * [Minutes] accepts [SleepTimer.MIN_MINUTES]..[SleepTimer.MAX_MINUTES] only.
 */
sealed interface SleepTimerOption {
    /** No timer. */
    data object Off : SleepTimerOption

    /** Stops [minutes] after it was set, with a [SleepTimer.FADE_MILLIS] fade-out before. */
    data class Minutes(val minutes: Int) : SleepTimerOption {
        init {
            require(minutes in SleepTimer.MIN_MINUTES..SleepTimer.MAX_MINUTES) {
                "Sleep timer minutes must be in ${SleepTimer.MIN_MINUTES}..${SleepTimer.MAX_MINUTES}, was $minutes"
            }
        }
    }

    /** Stops at the end of the current episode. */
    data object EndOfEpisode : SleepTimerOption
}

/** What the player does after [SleepTimer.tick]. */
sealed interface SleepTimerAction {
    /** Nothing to do (full volume). */
    data object None : SleepTimerAction

    /** Set the volume to [volume] (1 → 0, linear over the last [SleepTimer.FADE_MILLIS]). */
    data class Fade(val volume: Float) : SleepTimerAction

    /** Pause now (not a manual pause) and restore the volume; the timer is already off. */
    data object Stop : SleepTimerAction
}

/** What the UI shows: the option, its deadline (epoch millis, [SleepTimerOption.Minutes] only) and the time left. */
data class SleepTimerState(
    val option: SleepTimerOption = SleepTimerOption.Off,
    val deadlineMillis: Long? = null,
    val remainingMillis: Long? = null,
) {
    val isActive: Boolean get() = option != SleepTimerOption.Off
}

/**
 * Sleep timer state machine: plain, no coroutine, time from [clock] so tests can pin it. The
 * player calls [tick] periodically while a timer runs, [onManualPause] when the **user** pauses
 * (audio focus loss or unplugged headphones do not cancel) and [onItemEnded] at the end of an
 * episode.
 */
class SleepTimer(private val clock: () -> Long = System::currentTimeMillis) {
    private var option: SleepTimerOption = SleepTimerOption.Off
    private var deadlineMillis: Long? = null

    /** The option, the deadline and the time left now. */
    val state: SleepTimerState
        get() {
            val deadline = deadlineMillis
            return SleepTimerState(
                option = option,
                deadlineMillis = deadline,
                remainingMillis = deadline?.let { (it - clock()).coerceAtLeast(0L) },
            )
        }

    /** Replaces the current option; [SleepTimerOption.Minutes] counts from now. */
    fun set(option: SleepTimerOption) {
        when (option) {
            is SleepTimerOption.Minutes -> {
                this.option = option
                deadlineMillis = clock() + option.minutes * MINUTE_MILLIS
            }

            SleepTimerOption.EndOfEpisode -> {
                this.option = option
                deadlineMillis = null
            }

            SleepTimerOption.Off -> cancel()
        }
    }

    /**
     * [SleepTimerAction.Fade] in the last [FADE_MILLIS], [SleepTimerAction.Stop] once the
     * deadline is reached (the timer is then off), otherwise [SleepTimerAction.None].
     */
    fun tick(): SleepTimerAction {
        val deadline = deadlineMillis ?: return SleepTimerAction.None
        val remaining = deadline - clock()
        return when {
            remaining <= 0L -> {
                cancel()
                SleepTimerAction.Stop
            }

            remaining <= FADE_MILLIS -> SleepTimerAction.Fade(remaining.toFloat() / FADE_MILLIS)
            else -> SleepTimerAction.None
        }
    }

    /** The user paused: any timer is cancelled, also while it fades. */
    fun onManualPause() {
        cancel()
    }

    /** True when the timer stops at the end of this episode ([SleepTimerOption.EndOfEpisode]), which turns it off. */
    fun onItemEnded(): Boolean {
        if (option != SleepTimerOption.EndOfEpisode) return false
        cancel()
        return true
    }

    private fun cancel() {
        option = SleepTimerOption.Off
        deadlineMillis = null
    }

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 180
        const val MINUTE_MILLIS = 60_000L

        /** Length of the linear fade-out before a [SleepTimerOption.Minutes] timer stops. */
        const val FADE_MILLIS = 10_000L

        /** The minutes the Player offers, in order; the sheet adds "End of episode" and "Off". */
        val PRESET_MINUTES: List<Int> = listOf(5, 10, 15, 30, 45, 60)

        fun clampMinutes(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
    }
}
