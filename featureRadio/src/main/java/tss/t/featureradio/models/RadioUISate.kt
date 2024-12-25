package tss.t.featureradio.models

import tss.t.coreradio.models.RadioChannel

data class RadioUISate(
    val listRadio: List<RadioChannel> = emptyList(),
    val isLoading: Boolean = false
) {
}