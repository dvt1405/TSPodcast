package tss.t.ads

/**
 * A native-ad row inside a list. [slotKey] is stable for a given position
 * ("ad-0", "ad-1", ...), so LazyColumn keys stay stable and the renderer can
 * reuse an already-loaded ad when the row scrolls back into view.
 */
data class AdSlotRow(val placement: AdPlacement, val slotKey: String) {
    val lazyKey: String get() = "${placement.key}/$slotKey"
}

/**
 * Inserts [AdSlotRow]s into [items] at deterministic positions: after item
 * [AdsSettings.nativeFirstIndex], then every [AdsSettings.nativeInterval] items,
 * at most [AdsSettings.nativeMaxPerList] times. Returns [items] unchanged when
 * the placement is disabled.
 *
 * The AppLovin version picked random positions, which broke stable keys and
 * caused the `item as Episode` crash.
 */
fun <T : Any> interleaveNativeSlots(
    items: List<T>,
    placement: AdPlacement,
    settings: AdsSettings,
): List<Any> {
    if (!settings.isEnabled(placement)) return items
    return interleaveAt(
        items,
        placement,
        nativeSlotIndices(
            items.size,
            first = settings.nativeFirstIndex,
            interval = settings.nativeInterval,
            max = settings.nativeMaxPerList,
        ),
    )
}

/**
 * The Player's Up next list with its `player_up_next` rows
 * (docs/in-progress/plans/player-ads-and-premium.md §3.2): after item
 * [AdsSettings.playerUpNextFirstIndex] (never the first row), then every
 * [AdsSettings.playerUpNextInterval], at most [AdsSettings.playerUpNextMax], never last, so a
 * queue of 3 or fewer has none. [allowed] is the policy decision for the open list
 * ([AdPolicy.canShowPlayerInline]); without it the list is returned unchanged.
 */
fun <T : Any> interleaveUpNextSlots(
    items: List<T>,
    settings: AdsSettings,
    allowed: Boolean,
): List<Any> {
    if (!allowed || !settings.isEnabled(AdPlacement.PlayerUpNext)) return items
    return interleaveAt(items, AdPlacement.PlayerUpNext, upNextSlotIndices(items.size, settings))
}

/** 0-based indices of the Up next items an ad row follows (see [interleaveUpNextSlots]). */
fun upNextSlotIndices(itemCount: Int, settings: AdsSettings): List<Int> = nativeSlotIndices(
    itemCount,
    first = settings.playerUpNextFirstIndex.coerceAtLeast(2),
    interval = settings.playerUpNextInterval,
    max = settings.playerUpNextMax,
)

/**
 * 0-based indices of the items an ad row follows: after item [first] (1-based), then every
 * [interval] items, at most [max] times, never after the last item (a list never ends on an ad).
 * Same as shared `NativeSlots.indices`.
 */
fun nativeSlotIndices(itemCount: Int, first: Int, interval: Int, max: Int): List<Int> {
    if (max <= 0) return emptyList()
    val start = first.coerceAtLeast(1)
    val step = interval.coerceAtLeast(1)
    val out = ArrayList<Int>()
    for (index in 0 until itemCount) {
        val position = index + 1
        val isSlot = position == start || (position > start && (position - start) % step == 0)
        if (isSlot && out.size < max && position < itemCount) out.add(index)
    }
    return out
}

private fun <T : Any> interleaveAt(
    items: List<T>,
    placement: AdPlacement,
    after: List<Int>,
): List<Any> {
    if (after.isEmpty()) return items
    val out = ArrayList<Any>(items.size + after.size)
    var adCount = 0
    items.forEachIndexed { index, item ->
        out.add(item)
        if (adCount < after.size && after[adCount] == index) {
            out.add(AdSlotRow(placement, "ad-$adCount"))
            adCount++
        }
    }
    return out
}
