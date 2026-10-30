package tss.t.shared.playback

import tss.t.shared.util.EpochClock

/**
 * What the sleep timer is set to (M6 §3.1, audio-playback-ios §7). [Minutes] accepts
 * [SleepTimer.MIN_MINUTES]..[SleepTimer.MAX_MINUTES] only: callers with a free value (the
 * Shortcuts intent) clamp it first with [SleepTimer.clampMinutes].
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

    /** Stops at the end of the current item, which then does not advance. */
    data object EndOfEpisode : SleepTimerOption
}

/** What the player does after [SleepTimer.tick]. */
sealed interface SleepTimerAction {
    /** Nothing to do (full volume). */
    data object None : SleepTimerAction

    /** Set the volume to [volume] (1 → 0, linear over the last [SleepTimer.FADE_MILLIS]). */
    data class Fade(val volume: Float) : SleepTimerAction

    /** Pause now (not a manual pause) and restore the volume; the timer is already [SleepTimerOption.Off]. */
    data object Stop : SleepTimerAction
}

/**
 * What the UI shows: the option, its deadline (epoch millis, [SleepTimerOption.Minutes] only)
 * and the time left at the moment the state was read.
 */
data class SleepTimerState(
    val option: SleepTimerOption,
    val deadlineMillis: Long?,
    val remainingMillis: Long?,
)

/**
 * Sleep timer state machine (M6 §3.1): plain, no coroutine, time from [clock] so tests can pin
 * it. The player calls [tick] from its periodic time observer (it keeps running in the
 * background while audio plays) and when playback starts, [onManualPause] when the **user**
 * pauses (an interruption or a route change does not cancel) and [onItemEnded] at the end of
 * an item.
 */
class SleepTimer(private val clock: EpochClock) {
    private var option: SleepTimerOption = SleepTimerOption.Off
    private var deadlineMillis: Long? = null

    /** The option, the deadline and the time left now. */
    val state: SleepTimerState
        get() {
            val deadline = deadlineMillis
            return SleepTimerState(
                option = option,
                deadlineMillis = deadline,
                remainingMillis = deadline?.let { (it - clock.nowMillis()).coerceAtLeast(0L) },
            )
        }

    /** Replaces the current option; [SleepTimerOption.Minutes] counts from now. */
    fun set(option: SleepTimerOption) {
        when (option) {
            is SleepTimerOption.Minutes -> {
                this.option = option
                deadlineMillis = clock.nowMillis() + option.minutes * MINUTE_MILLIS
            }
            SleepTimerOption.EndOfEpisode -> {
                this.option = option
                deadlineMillis = null
            }
            SleepTimerOption.Off -> cancel()
        }
    }

    /**
     * [SleepTimerAction.Fade] in the last [FADE_MILLIS] (`remaining / FADE_MILLIS`),
     * [SleepTimerAction.Stop] once the deadline is reached (the timer is then off), otherwise
     * [SleepTimerAction.None].
     */
    fun tick(): SleepTimerAction {
        val deadline = deadlineMillis ?: return SleepTimerAction.None
        val remaining = deadline - clock.nowMillis()
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

    /** True when the timer stops at the end of this item ([SleepTimerOption.EndOfEpisode]), which turns it off. */
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

        /** A free value (Shortcuts) brought into [MIN_MINUTES]..[MAX_MINUTES]. */
        fun clampMinutes(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
    }
}
