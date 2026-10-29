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
    if (!settings.isEnabled(placement) || settings.nativeMaxPerList <= 0) return items
    val first = settings.nativeFirstIndex.coerceAtLeast(1)
    val interval = settings.nativeInterval.coerceAtLeast(1)
    val out = ArrayList<Any>(items.size + settings.nativeMaxPerList)
    var adCount = 0
    items.forEachIndexed { index, item ->
        out.add(item)
        val position = index + 1
        val isSlot = position == first || (position > first && (position - first) % interval == 0)
        // Never end the list on an ad.
        if (isSlot && adCount < settings.nativeMaxPerList && position < items.size) {
            out.add(AdSlotRow(placement, "ad-$adCount"))
            adCount++
        }
    }
    return out
}
