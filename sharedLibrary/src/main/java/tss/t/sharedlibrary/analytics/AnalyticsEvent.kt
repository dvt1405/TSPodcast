package tss.t.sharedlibrary.analytics

/**
 * One event in GA4 shape. Build it with [of], never with the constructor directly: [of]
 * enforces the GA4 limits that Firebase otherwise enforces by silently dropping the event
 * or the parameter.
 *
 * - name: snake_case, starts with a letter, ≤ [MAX_NAME] characters;
 * - at most [MAX_PARAMS] parameters (the first ones win);
 * - parameter keys: same rules as names;
 * - values: [String] (≤ [MAX_VALUE] characters, cut without splitting an emoji), [Long] or
 *   [Double]. Int/Short/Byte become Long, Float becomes Double, Boolean becomes `"true"` /
 *   `"false"` (GA4 has no boolean), enums their lowercase name, null drops the parameter.
 */
data class AnalyticsEvent internal constructor(
    val name: String,
    val params: Map<String, Any>,
) {
    override fun toString(): String = "$name $params"

    companion object {
        const val MAX_NAME = 40
        const val MAX_PARAMS = 25
        const val MAX_VALUE = 100

        fun of(name: String, vararg params: Pair<String, Any?>): AnalyticsEvent =
            of(name, params.toList())

        fun of(name: String, params: List<Pair<String, Any?>>): AnalyticsEvent {
            val out = LinkedHashMap<String, Any>()
            for ((key, raw) in params) {
                if (out.size >= MAX_PARAMS) break
                val value = normalize(raw) ?: continue
                out[sanitizeName(key)] = value
            }
            return AnalyticsEvent(sanitizeName(name), out)
        }

        /**
         * `"Podcast Open!"` → `podcast_open`. Lowercase ASCII letters, digits and `_`, starting
         * with a letter (GA4 rejects anything else), cut to [MAX_NAME].
         */
        fun sanitizeName(raw: String): String {
            val sb = StringBuilder(raw.length)
            for (c in raw.trim().lowercase()) {
                when {
                    c in 'a'..'z' || c in '0'..'9' -> sb.append(c)
                    c == '_' || c == ' ' || c == '-' || c == '.' -> if (sb.isNotEmpty() && sb.last() != '_') sb.append('_')
                }
            }
            while (sb.isNotEmpty() && !sb[0].isLetter()) sb.deleteCharAt(0)
            val name = sb.toString().trimEnd('_').ifEmpty { "unnamed" }
            return if (name.length <= MAX_NAME) name else name.substring(0, MAX_NAME).trimEnd('_')
        }

        /** String value cut to [MAX_VALUE] characters, never between the two halves of an emoji. */
        fun trimValue(value: String): String {
            if (value.length <= MAX_VALUE) return value
            val end = if (value[MAX_VALUE - 1].isHighSurrogate()) MAX_VALUE - 1 else MAX_VALUE
            return value.substring(0, end)
        }

        internal fun normalize(raw: Any?): Any? = when (raw) {
            null -> null
            is String -> trimValue(raw)
            is Long -> raw
            is Int -> raw.toLong()
            is Short -> raw.toLong()
            is Byte -> raw.toLong()
            is Double -> raw.takeIf { it.isFinite() }
            is Float -> raw.toDouble().takeIf { it.isFinite() }
            is Boolean -> raw.toString()
            is Enum<*> -> raw.name.lowercase()
            else -> trimValue(raw.toString())
        }
    }
}
