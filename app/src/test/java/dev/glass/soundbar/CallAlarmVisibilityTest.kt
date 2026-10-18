package dev.glass.soundbar

import org.junit.Assert.*
import org.junit.Test

class CallAlarmVisibilityTest {
    private fun retain(
        enabled: Boolean = true, expanded: Boolean = true, stream: Int = 4, important: Boolean = true,
        callMode: Boolean = true, multiApp: Boolean = false, accessibility: Boolean = false,
    ) = NativePanelRows.retainAlarmVisibility(enabled, expanded, stream, important, callMode, multiApp, accessibility)

    @Test fun alarmExcludedByNativeCallPolicyReachesTheFiveRowDifferSnapshot() {
        val rows = listOf(0, 3, 2, 5, 4)
        val nativeVisible = rows.filter { it != 4 }
        assertFalse(NativePanelRows.needsExtraRows(nativeVisible))
        val visible = rows.filter { it in nativeVisible || retain(stream = it) }
        assertTrue(NativePanelRows.needsExtraRows(visible))
        val cache = CompleteSystemPanelList(emptyList(), visible)
        cache.addAll(visible.subList(0, 4))
        assertEquals(rows, ArrayList(cache))
        assertTrue(cache.containsAll(listOf(0, 4)))
    }

    @Test fun callEndAndModuleDisableLeaveTheNativeVisibilityPolicyInControl() {
        assertTrue(retain())
        assertFalse(retain(callMode = false))
        assertFalse(retain(enabled = false))
        assertFalse(retain(expanded = false))
    }

    @Test fun onlyTheImportantAlarmCallExclusionIsRemoved() {
        for (stream in listOf(0, 2, 3, 5, 6, 10, 11, 10001)) assertFalse(retain(stream = stream))
        assertFalse(retain(important = false))
        assertFalse(retain(multiApp = true))
        assertFalse(retain(accessibility = true))
    }
}
