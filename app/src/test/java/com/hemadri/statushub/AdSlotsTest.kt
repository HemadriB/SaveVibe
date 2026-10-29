package com.hemadri.statushub

import com.hemadri.statushub.ui.components.GridEntry
import com.hemadri.statushub.ui.components.withAdSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSlotsTest {

    private fun adPositions(count: Int) =
        withAdSlots((1..count).toList(), "t").mapIndexedNotNull { i, e -> i.takeIf { e is GridEntry.Ad } }

    @Test
    fun shortListsGetNoAds() {
        assertTrue(adPositions(0).isEmpty())
        assertTrue(adPositions(6).isEmpty()) // never a trailing ad
    }

    @Test
    fun firstAdAfterTwoRowsThenEveryFiveRows() {
        val entries = withAdSlots((1..40).toList(), "t")
        val ads = entries.filterIsInstance<GridEntry.Ad>()
        assertEquals(listOf("t_0", "t_1", "t_2"), ads.map { it.slot })
        // after 6 items, after 21 items, after 36 items
        assertEquals(listOf(6, 22, 38), adPositions(40))
        // every real item is still there, in order
        assertEquals((1..40).toList(), entries.filterIsInstance<GridEntry.Item<Int>>().map { it.value })
    }
}
