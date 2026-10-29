package tss.t.shared.model

import kotlinx.serialization.Serializable

/** `persons[]` of an episode (Android: `Person`). */
@Serializable
data class Person(
    val group: String = "",
    val href: String = "",
    val id: Long = 0L,
    val img: String = "",
    val name: String = "",
    val role: String = "",
)

/** `transcripts[]` of an episode (Android: `PodcastTranscript`). */
@Serializable
data class PodcastTranscript(
    val type: String = "",
    val url: String = "",
)

/**
 * `soundbite` / `soundbites[]` of an episode (Android: `Soundbite`).
 *
 * The namespace allows fractional seconds (`startTime="73.0"`), so these are Doubles;
 * Android's Int fields would reject them.
 */
@Serializable
data class Soundbite(
    val duration: Double = 0.0,
    val startTime: Double = 0.0,
    val title: String = "",
)

/** `socialInteract[]` of an episode (Android: `SocialInteract`). */
@Serializable
data class SocialInteract(
    val accountId: String = "",
    val accountUrl: String = "",
    val priority: Int = 0,
    val protocol: String = "",
    val url: String = "",
)

/** `funding` of a feed (Android: `Funding`). */
@Serializable
data class Funding(
    val message: String = "",
    val url: String? = null,
)

/** `value` block of a feed or episode (Android: `Value`). */
@Serializable
data class Value(
    val destinations: List<Destination> = emptyList(),
    val model: ValueModel? = null,
)

/** `value.model` (Android: `Model` in Feed.kt). */
@Serializable
data class ValueModel(
    val method: String = "",
    val suggested: String? = null,
    val type: String = "",
)

/** `value.destinations[]` (Android: `Destination`). */
@Serializable
data class Destination(
    val address: String = "",
    val customKey: String? = null,
    val customValue: String? = null,
    val fee: Boolean? = null,
    val name: String = "",
    val split: Int = 0,
    val type: String = "",
)
