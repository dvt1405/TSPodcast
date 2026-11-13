package tss.t.podcast.ui.screens.paywall

/**
 * An ISO 8601 period as Play returns it (`P1W`, `P1M`, `P3M`, `P1Y`, `P7D`), for display.
 * Play uses a single unit per period; anything else is null and the paywall shows the price
 * alone.
 */
data class BillingPeriod(val count: Int, val unit: Unit) {
    enum class Unit { Day, Week, Month, Year }

    companion object {
        private val ISO = Regex("""P(\d+)([DWMY])""")

        fun parse(iso: String?): BillingPeriod? {
            val match = ISO.matchEntire(iso?.trim()?.uppercase() ?: return null) ?: return null
            val count = match.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val unit = when (match.groupValues[2]) {
                "D" -> Unit.Day
                "W" -> Unit.Week
                "M" -> Unit.Month
                else -> Unit.Year
            }
            return BillingPeriod(count, unit)
        }
    }
}
