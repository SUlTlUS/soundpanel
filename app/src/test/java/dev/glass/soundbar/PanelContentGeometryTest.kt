package dev.glass.soundbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelContentGeometryTest {
    @Test fun unmeasuredCallRowDoesNotHideUsableMediaRowGeometry() {
        val call = PanelContentGeometry.rowWidths(-1, 0, -1, 0)
        val media = PanelContentGeometry.rowWidths(-1, 192, -1, 156)
        assertNull(call)
        assertEquals(PanelContentGeometry.RowWidths(192, 156), listOfNotNull(call, media).firstOrNull())
    }

    @Test fun explicitNativeWidthsDoNotRequireAPreviousCallLayout() {
        assertEquals(PanelContentGeometry.RowWidths(192, 156), PanelContentGeometry.rowWidths(192, 0, 156, 0))
        assertNull(PanelContentGeometry.rowWidths(0, 0, 156, 156))
        assertNull(PanelContentGeometry.rowWidths(192, 192, 0, 0))
        assertNull(PanelContentGeometry.rowWidths(120, 120, 156, 156))
    }

    @Test fun landscapeTopClampHasAStableLayoutTarget() {
        assertEquals(0, PanelContentGeometry.topMargin(18, 28f, 0))
        assertEquals(0, PanelContentGeometry.topMargin(32, 28f, 12))
    }

    @Test fun topMarginAccountsForTheWindowParentOffset() {
        assertEquals(72, PanelContentGeometry.topMargin(120, 24f, 24))
        assertEquals(0, PanelContentGeometry.topMargin(40, 24f, 24))
    }

    @Test fun hiddenLabelsExcludeAsymmetricNativeFrameInsets() {
        val rowHeight = PanelContentGeometry.compactHeight(784, 112, 0)
        val capsuleHeight = rowHeight - 14 - 28
        val panelHeight = PanelContentGeometry.symmetricHeight(capsuleHeight, 28f)
        assertEquals(630, capsuleHeight)
        assertEquals(686, panelHeight)
        assertEquals(28, panelHeight - 28 - capsuleHeight)
    }

    @Test fun finalHeightUsesVisibleMaterialBoundsRatherThanFrameHeight() {
        // A ROM material inset shortens the visible capsule beyond frame padding.
        val visibleCapsuleHeight = 618
        val panelHeight = PanelContentGeometry.symmetricHeight(visibleCapsuleHeight, 24f)
        assertEquals(666, panelHeight)
        assertEquals(24, panelHeight - 24 - visibleCapsuleHeight)
    }

    @Test fun compactTextPreservesSliderHeight() {
        assertEquals(734, PanelContentGeometry.compactHeight(784, 112, 62))
        assertEquals(672, PanelContentGeometry.compactHeight(784, 112, 62) - 62)
    }

    @Test fun hiddenTextReclaimsEntireReservedArea() {
        assertEquals(672, PanelContentGeometry.compactHeight(784, 112, 0))
    }
}
