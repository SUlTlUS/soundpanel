package dev.glass.soundbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelSettingsTest {
    @Test fun savedDisabledButtonsAndHiddenLabelsSurviveSnapshotDecode() {
        val saved = PanelSettings.fromValues(mapOf("ringer_button" to false,
            "dnd_button" to false, "headset_button" to false, "hide_labels" to true, "tone" to -0.6f))
        assertFalse(saved.ringerButton)
        assertFalse(saved.dndButton)
        assertFalse(saved.headsetButton)
        assertTrue(saved.hideLabels)
        assertEquals(-0.6f, saved.tone)
    }

    @Test fun previousSettingsHaveVisibleLabelsByDefault() {
        assertFalse(PanelSettings.fromValues(mapOf("ringer_button" to false)).hideLabels)
    }
}
