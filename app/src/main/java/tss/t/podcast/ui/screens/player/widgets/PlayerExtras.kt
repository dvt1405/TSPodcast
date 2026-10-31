package tss.t.podcast.ui.screens.player.widgets

import tss.t.sharedlibrary.ui.glass.LocalLiquidGlass
import tss.t.sharedlibrary.ui.glass.glassSurface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tss.t.sharedlibrary.theme.TextStyles
import tss.t.sharedplayer.player.PlaybackExtrasState
import tss.t.sharedplayer.playback.PlaybackSpeed
import tss.t.sharedplayer.playback.SleepTimer
import tss.t.sharedplayer.playback.SleepTimerOption
import tss.t.sharedresources.R
import kotlin.math.roundToInt

/**
 * Speed and sleep-timer buttons under the Player controls (parity with iOS M6 "player cơ bản").
 * Each opens a bottom sheet; the values live in `PlayerManager`, so they keep working with the
 * screen closed and from the notification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerExtrasRow(
    state: PlaybackExtrasState,
    onSpeedChanged: (Int) -> Unit,
    onApplyToAllChanged: (Boolean) -> Unit,
    onSleepTimerSelected: (SleepTimerOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSpeed by rememberSaveable { mutableStateOf(false) }
    var showTimer by rememberSaveable { mutableStateOf(false) }
    val glass = LocalLiquidGlass.current
    val capsule = RoundedCornerShape(50)
    val chipModifier = if (glass) Modifier.glassSurface(capsule, elevation = 4.dp) else Modifier
    val chipBorder = if (glass) null else BorderStroke(1.dp, androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.4f))

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { showSpeed = true },
            modifier = chipModifier,
            shape = capsule,
            border = chipBorder,
        ) {
            Text(
                text = PlaybackSpeed.label(state.speedTenths),
                fontWeight = FontWeight.SemiBold
            )
        }
        val timer = state.sleepTimer
        OutlinedButton(
            onClick = { showTimer = true },
            modifier = chipModifier,
            shape = capsule,
            border = chipBorder,
        ) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = when (val option = timer.option) {
                    SleepTimerOption.Off -> stringResource(R.string.player_sleep_title)
                    SleepTimerOption.EndOfEpisode -> stringResource(R.string.player_sleep_active_end_of_episode)
                    is SleepTimerOption.Minutes -> stringResource(
                        R.string.player_sleep_remaining,
                        formatRemaining(timer.remainingMillis ?: option.minutes * SleepTimer.MINUTE_MILLIS)
                    )
                },
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    if (showSpeed) {
        ModalBottomSheet(
            onDismissRequest = { showSpeed = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            SpeedSheetContent(state, onSpeedChanged, onApplyToAllChanged)
        }
    }
    if (showTimer) {
        ModalBottomSheet(
            onDismissRequest = { showTimer = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            SleepTimerSheetContent(state) {
                onSleepTimerSelected(it)
                showTimer = false
            }
        }
    }
}

@Composable
private fun SpeedSheetContent(
    state: PlaybackExtrasState,
    onSpeedChanged: (Int) -> Unit,
    onApplyToAllChanged: (Boolean) -> Unit,
) {
    // The slider moves freely while dragged and commits on release, so the store is written once.
    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(state.speedTenths.toFloat()) }
    val shown = if (dragging) sliderValue.roundToInt() else state.speedTenths
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        Text(stringResource(R.string.player_speed_title), style = TextStyles.Title4)
        if (state.isLive) {
            Text(
                stringResource(R.string.player_speed_live),
                style = TextStyles.SubTitle3,
                modifier = Modifier.padding(top = 12.dp)
            )
            return@Column
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { onSpeedChanged(state.speedTenths - 1) },
                enabled = state.speedTenths > PlaybackSpeed.MIN_TENTHS
            ) {
                Icon(Icons.Rounded.Remove, stringResource(R.string.player_speed_slower))
            }
            Text(
                PlaybackSpeed.label(shown),
                style = TextStyles.Title3,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = { onSpeedChanged(state.speedTenths + 1) },
                enabled = state.speedTenths < PlaybackSpeed.MAX_TENTHS
            ) {
                Icon(Icons.Rounded.Add, stringResource(R.string.player_speed_faster))
            }
        }
        Slider(
            value = if (dragging) sliderValue else state.speedTenths.toFloat(),
            onValueChange = {
                dragging = true
                sliderValue = it
            },
            onValueChangeFinished = {
                dragging = false
                onSpeedChanged(sliderValue.roundToInt())
            },
            valueRange = PlaybackSpeed.MIN_TENTHS.toFloat()..PlaybackSpeed.MAX_TENTHS.toFloat(),
            steps = PlaybackSpeed.MAX_TENTHS - PlaybackSpeed.MIN_TENTHS - 1,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PlaybackSpeed.CHIPS.forEach { tenths ->
                FilterChip(
                    selected = state.speedTenths == tenths,
                    onClick = { onSpeedChanged(tenths) },
                    label = { Text(PlaybackSpeed.label(tenths)) }
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { onApplyToAllChanged(!state.applyToAll) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.player_speed_apply_all), style = TextStyles.SubTitle2)
                Text(stringResource(R.string.player_speed_apply_all_hint), style = TextStyles.SubTitle4)
            }
            Switch(checked = state.applyToAll, onCheckedChange = onApplyToAllChanged)
        }
    }
}

@Composable
private fun SleepTimerSheetContent(
    state: PlaybackExtrasState,
    onSelected: (SleepTimerOption) -> Unit,
) {
    val current = state.sleepTimer.option
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        Text(
            stringResource(R.string.player_sleep_title),
            style = TextStyles.Title4,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        SleepTimer.PRESET_MINUTES.forEach { minutes ->
            TimerRow(
                label = stringResource(R.string.player_sleep_minutes, minutes),
                selected = (current as? SleepTimerOption.Minutes)?.minutes == minutes,
                onClick = { onSelected(SleepTimerOption.Minutes(minutes)) }
            )
        }
        TimerRow(
            label = stringResource(R.string.player_sleep_end_of_episode),
            selected = current == SleepTimerOption.EndOfEpisode,
            onClick = { onSelected(SleepTimerOption.EndOfEpisode) }
        )
        if (current != SleepTimerOption.Off) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            TextButton(
                onClick = { onSelected(SleepTimerOption.Off) },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(stringResource(R.string.player_sleep_off))
            }
        }
    }
}

@Composable
private fun TimerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TextStyles.SubTitle2, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null)
    }
}

/** `M:SS`, or `H:MM:SS` from one hour. */
internal fun formatRemaining(millis: Long): String {
    val total = (millis.coerceAtLeast(0L) + 999L) / 1000L
    val hours = total / 3600L
    val minutes = (total / 60L) % 60L
    val seconds = total % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
