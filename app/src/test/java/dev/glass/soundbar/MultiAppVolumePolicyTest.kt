package dev.glass.soundbar

import org.junit.Assert.*
import org.junit.Test

class MultiAppVolumePolicyTest {
    private data class Row(val stream: Int, val app: Boolean = false)
    private val game = Row(10001, true)
    private val video = Row(10002, true)
    private val media = Row(3)
    private val remote = Row(10003)
    private val hidden = PanelSettings(hideMultiAppVolume = true)

    @Test fun oldSettingsKeepNativeMultiAppUiAndModuleDisableOverridesTheSwitch() {
        assertFalse(PanelSettings.fromValues(emptyMap<String, Any>()).hideMultiAppVolume)
        val saved = PanelSettings.fromValues(mapOf("hide_multi_app_volume" to true))
        assertTrue(MultiAppVolumePolicy.hides(saved))
        assertFalse(MultiAppVolumePolicy.hides(saved.copy(enabled = false)))
        assertFalse(MultiAppVolumePolicy.hides(saved.copy(hideMultiAppVolume = false)))
    }

    @Test fun screenshotGameAndVideoSlidersDisappearWhileMainMediaSliderRemains() {
        val repositoryRows = listOf(game, video, media)
        val visible = MultiAppVolumePolicy.visibleRows(repositoryRows, hidden, false) { it.app }
        assertEquals(listOf(media), visible)
        assertSame(media, visible.single())
        assertEquals(listOf(game, video, media), repositoryRows)
    }

    @Test fun manualAppVolumePanelAndSwitchOffKeepAllNativeRows() {
        val rows = listOf(game, video, media)
        assertSame(rows, MultiAppVolumePolicy.visibleRows(rows, hidden, true) { it.app })
        assertSame(rows, MultiAppVolumePolicy.visibleRows(rows, hidden.copy(hideMultiAppVolume = false), false) { it.app })
        assertSame(rows, MultiAppVolumePolicy.visibleRows(rows, hidden.copy(enabled = false), false) { it.app })
        assertFalse(MultiAppVolumePolicy.hidesRow(hidden, expanded = true, isMultiAppRow = true))
        assertTrue(MultiAppVolumePolicy.hidesRow(hidden, expanded = false, isMultiAppRow = true))
    }

    @Test fun dynamicStreamsAndSystemRowsKeepTheirNativeOrderAndIdentity() {
        val call = Row(0)
        val accessibility = Row(10)
        val rows = listOf(call, game, remote, accessibility, video, media)
        assertEquals(listOf(call, remote, accessibility, media),
            MultiAppVolumePolicy.visibleRows(rows, hidden, false) { it.app })
        assertFalse(MultiAppVolumePolicy.hidesRow(hidden, expanded = false, isMultiAppRow = false))
    }

    @Test fun refreshingPlaybackRowsUsesTheCurrentListAndRestoringTheSwitchNeedsNoAudioReset() {
        val rows = mutableListOf(game, media)
        assertEquals(listOf(media), MultiAppVolumePolicy.visibleRows(rows, hidden, false) { it.app })
        rows.add(1, video)
        assertEquals(listOf(media), MultiAppVolumePolicy.visibleRows(rows, hidden, false) { it.app })
        assertEquals(listOf(game, video, media), MultiAppVolumePolicy.visibleRows(rows, PanelSettings(), false) { it.app })
        assertEquals(emptyList<Row>(), MultiAppVolumePolicy.visibleRows(emptyList<Row>(), hidden, false) { it.app })
    }
}
