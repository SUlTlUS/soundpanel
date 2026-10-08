package dev.glass.soundbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelMorphGeometryTest {
    @Test fun materialEdgeFollowsActualRowPositionWithoutAClock() {
        assertEquals(210, PanelMorphGeometry.followedLeft(400, 100f, 230f, 20f))
        assertEquals(60, PanelMorphGeometry.followedLeft(400, 100f, 80f, 20f))
        assertEquals(0, PanelMorphGeometry.followedLeft(400, 100f, 20f, 20f))
    }

    @Test fun nativeSpringOvershootStaysInsideMaterialBounds() {
        assertEquals(0, PanelMorphGeometry.followedLeft(400, 100f, -10f, 20f))
        assertEquals(300, PanelMorphGeometry.followedLeft(400, 100f, 450f, 20f))
    }

    @Test fun springTailCannotReverseTheMaterial() {
        assertEquals(10, PanelMorphGeometry.advanceLeft(10, 15, true))
        assertEquals(290, PanelMorphGeometry.advanceLeft(290, 280, false))
    }

    @Test fun expandMovesOnlyLeftEdge() {
        val positions = (0..100).map { PanelMorphGeometry.left(400, 100f, 400f, it / 100f) }
        assertEquals(300, positions.first())
        assertEquals(0, positions.last())
        assertTrue(positions.zipWithNext().all { (before, after) -> after <= before })
    }

    @Test fun dismissRetracesTheSameShape() {
        for (step in 0..100) {
            assertEquals(
                PanelMorphGeometry.left(400, 100f, 400f, step / 100f),
                PanelMorphGeometry.left(400, 400f, 100f, 1f - step / 100f),
            )
        }
    }

    @Test fun interruptStartsAtTheVisibleWidth() {
        val left = PanelMorphGeometry.left(400, 100f, 400f, 0.4f)
        val currentWidth = 400f - left
        assertEquals(left, PanelMorphGeometry.left(400, currentWidth, 100f, 0f))
        assertEquals(300, PanelMorphGeometry.left(400, currentWidth, 100f, 1f))
    }

    @Test fun dimensionsAndProgressCannotRevealOutsideThePanel() {
        assertEquals(400, PanelMorphGeometry.left(400, -50f, 500f, -1f))
        assertEquals(0, PanelMorphGeometry.left(400, -50f, 500f, 2f))
    }
}
