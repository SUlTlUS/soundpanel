package dev.glass.soundbar

import org.junit.Assert.*
import org.junit.Test

class CompleteSystemPanelListTest {
    private class Row(val stream: Int)
    private val call = Row(0)
    private val media = Row(3)
    private val ring = Row(2)
    private val notification = Row(5)
    private val alarm = Row(4)

    @Test fun onlyCallOrBluetoothCallWithAlarmNeedsExtraPanelCapacity() {
        assertTrue(NativePanelRows.needsExtraRows(listOf(0, 3, 2, 5, 4)))
        assertTrue(NativePanelRows.needsExtraRows(listOf(6, 3, 2, 5, 4)))
        assertFalse(NativePanelRows.needsExtraRows(listOf(3, 2, 5, 4)))
        assertFalse(NativePanelRows.needsExtraRows(listOf(10, 3, 2, 5, 4)))
        assertFalse(NativePanelRows.needsExtraRows(listOf(0, 10, 3, 2, 5)))
    }

    @Test fun nativeFourRowCapRetainsCallAndAlarmInTheSubmittedSnapshot() {
        val sorted = listOf(call, media, ring, notification, alarm)
        val cache = CompleteSystemPanelList(emptyList(), sorted)
        cache.addAll(sorted.subList(0, 4))
        val submitted = ArrayList(cache)
        assertEquals(sorted, submitted)
        assertSame(call, submitted.first())
        assertSame(alarm, submitted.last())
    }

    @Test fun nativeOrderIsPreservedWhenRepositoryOrderDiffers() {
        val repository = listOf(alarm, notification, media, ring, call)
        val cache = CompleteSystemPanelList(emptyList(), repository)
        cache.addAll(listOf(call, media, ring, notification))
        assertEquals(listOf(call, media, ring, notification, alarm), cache)
        assertEquals(listOf(alarm, notification, media, ring, call), repository)
    }

    @Test fun callEndingCanRefreshBackToFourRowsWithoutStaleCallOrDuplicates() {
        val normal = listOf(media, ring, notification, alarm)
        val cache = CompleteSystemPanelList(listOf(call), normal)
        cache.clear()
        cache.addAll(normal)
        assertEquals(normal, cache)
        assertFalse(cache.contains(call))
    }

    @Test fun unrelatedSelectionIsNotExpandedAndExistingCacheSurvivesUntilClear() {
        val visible = listOf(call, media, ring, notification, alarm)
        val cache = CompleteSystemPanelList(listOf(media), visible)
        assertEquals(listOf(media), cache)
        cache.clear()
        val app = Row(10001)
        cache.addAll(listOf(app, media, ring, notification))
        assertEquals(listOf(app, media, ring, notification), cache)
    }
}
