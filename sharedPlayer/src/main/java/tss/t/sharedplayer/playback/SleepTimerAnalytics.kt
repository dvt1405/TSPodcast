package tss.t.sharedplayer.playback

/** `option` of analytics `sleep_timer_set` (iOS parity): minutes, `end_of_episode` or `off`. */
fun SleepTimerOption.analyticsValue(): String = when (this) {
    is SleepTimerOption.Minutes -> minutes.toString()
    SleepTimerOption.EndOfEpisode -> "end_of_episode"
    SleepTimerOption.Off -> "off"
}
