package com.statushub.ui.components

/**
 * A grid entry: either real content or a full-width native ad row
 */
sealed interface GridEntry<out T> {
    data class Item<T>(val value: T, val index: Int) : GridEntry<T>
    data class Ad(val slot: String) : GridEntry<Nothing>
}

/**
 * Inserts native ad rows into a grid: first after [firstAfter] items (2 rows of 3),
 * then every [every] items (5 rows). Short lists get no ads at all.
 */
fun <T> withAdSlots(items: List<T>, slotPrefix: String, firstAfter: Int = 6, every: Int = 15): List<GridEntry<T>> {
    val result = ArrayList<GridEntry<T>>(items.size + items.size / every + 1)
    var adCount = 0
    items.forEachIndexed { index, item ->
        result += GridEntry.Item(item, index)
        val position = index + 1
        if (position < items.size &&
            (position == firstAfter || (position > firstAfter && (position - firstAfter) % every == 0))
        ) {
            result += GridEntry.Ad("${slotPrefix}_${adCount++}")
        }
    }
    return result
}
