package dev.glass.soundbar

import org.junit.Assert.*
import org.junit.Test

class NativeStrokeOpacityTest {
    // Mirrors the native copy/getters contract, including independent near/far alpha.
    data class Stroke(
        val strokeLineColor: String = "native-color",
        val strokeLineVerticalNearSolid: Float = 1f,
        val strokeLineVerticalNearFade: Float = 2f,
        val strokeLineVerticalFarSolid: Float = 3f,
        val strokeLineVerticalFarFade: Float = 4f,
        var strokeLineAlphaNear: Float = 0.4f,
        var strokeLineAlphaFar: Float = 0.8f,
        val strokeLineTransverseNearSolid: Float = 7f,
        val strokeLineTransverseNearFade: Float = 8f,
        val strokeLineTransverseFarSolid: Float = 9f,
        val strokeLineTransverseFarFade: Float = 10f,
        val ratio: Float = 0.2f,
        val strokeLinePow: Float = 12f,
        val strokeLineMix: Float = 0.5f,
    )

    @Test fun fadeChangesOnlyTheCopiedOpacity() {
        val original = Stroke()
        val faded = NativeStrokeOpacity(original).at(0.5f) as Stroke
        assertNotSame(original, faded)
        assertEquals(original.copy(strokeLineAlphaNear = 0.2f, strokeLineAlphaFar = 0.4f), faded)
        assertEquals(0.4f, original.strokeLineAlphaNear, 0f)
        assertEquals(0.8f, original.strokeLineAlphaFar, 0f)
    }

    @Test fun everyFrameUsesTheOriginalAlphaAndRestoresThePreset() {
        val original = Stroke()
        val fade = NativeStrokeOpacity(original)
        fade.at(0.5f)
        val quarter = fade.at(0.25f) as Stroke
        assertEquals(0.1f, quarter.strokeLineAlphaNear, 0f)
        assertEquals(0.2f, quarter.strokeLineAlphaFar, 0f)
        assertSame(original, fade.at(1f))
    }

    @Test fun fadeEndsFullyTransparentAndClampsNativeAmount() {
        val fade = NativeStrokeOpacity(Stroke())
        val hidden = fade.at(-1f) as Stroke
        assertEquals(0f, hidden.strokeLineAlphaNear, 0f)
        assertEquals(0f, hidden.strokeLineAlphaFar, 0f)
        assertSame(fade.original, fade.at(2f))
    }
}
