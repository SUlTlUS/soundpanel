package dev.glass.soundbar

import org.junit.Assert.*
import org.junit.Test

class NativePanelStrokeTest {
    data class Corner(var type: String = "FULL", var radius: Float = 34f, var weight: Float = 1f)
    class Config {
        var cornerRadius = 34f
        var enableStaticBlurCorner = true
        var gradientStrokeLineParam: NativeStrokeOpacityTest.Stroke? = NativeStrokeOpacityTest.Stroke()
        var opticsParams: String? = "native-optics"
        var innerShadowParams: String? = "native-shadow"
        val gradientStrokeCornerParam = Corner()

        fun nativePanelRefresh() {
            enableStaticBlurCorner = false
            gradientStrokeLineParam = null
            opticsParams = null
            innerShadowParams = null
            gradientStrokeCornerParam.type = "NONE"
            gradientStrokeCornerParam.radius = 0f
            gradientStrokeCornerParam.weight = 0f
        }
    }

    class Renderer {
        var shaderEnabled = false
        var stroke: NativeStrokeOpacityTest.Stroke? = null
        var corner: Corner? = null
        var optics: String? = null
        var shadow: String? = null
        var paintOpacity = 0
        fun setEnableBlurShader(enabled: Boolean) { shaderEnabled = enabled }
        fun setOpticsParams(value: String?) { optics = value }
        fun setInnerShadowParams(value: String?) { shadow = value }
        fun setAlpha(value: Int) { paintOpacity = value }
        fun setGradientStrokeLineParams(value: NativeStrokeOpacityTest.Stroke?): Boolean {
            val changed = stroke != value
            stroke = value
            return changed
        }
        fun setCornerParams(value: Corner): Boolean {
            val changed = corner != value
            corner = value.copy()
            return changed
        }
    }

    @Test fun actualRendererReceivesNativeShaderStrokeAndCorners() {
        val config = Config()
        val stroke = NativePanelStroke(config)
        val renderer = Renderer()
        config.nativePanelRefresh()
        stroke.applyTo(renderer, 1f)
        assertTrue(renderer.shaderEnabled)
        assertSame(config.gradientStrokeLineParam, renderer.stroke)
        assertEquals(Corner(), renderer.corner)
        assertEquals("native-optics", renderer.optics)
        assertEquals("native-shadow", renderer.shadow)
        assertEquals(255, renderer.paintOpacity)
        // Native setters return false when no update is needed; that is not failure.
        stroke.applyTo(renderer, 1f)
        assertSame(config.gradientStrokeLineParam, renderer.stroke)
    }

    @Test fun actualRendererReceivesCurrentDismissOpacity() {
        val stroke = NativePanelStroke(Config())
        val renderer = Renderer()
        stroke.applyTo(renderer, 0.25f)
        assertEquals(0.4f, renderer.stroke!!.strokeLineAlphaNear, 0f)
        assertEquals(0.8f, renderer.stroke!!.strokeLineAlphaFar, 0f)
        assertEquals(63, renderer.paintOpacity)
        stroke.applyTo(renderer, 0f)
        assertEquals(0, renderer.paintOpacity)
        stroke.applyTo(renderer, 1f)
        assertEquals(255, renderer.paintOpacity)
    }

    @Test fun stockRefreshRestoresTheNativeStrokeAndMatchingCorners() {
        val config = Config()
        val original = config.gradientStrokeLineParam
        val stroke = NativePanelStroke(config)
        config.nativePanelRefresh()
        stroke.restore(1f)
        assertSame(original, config.gradientStrokeLineParam)
        assertTrue(config.enableStaticBlurCorner)
        assertEquals(Corner(), config.gradientStrokeCornerParam)
        assertEquals("native-optics", config.opticsParams)
        assertEquals("native-shadow", config.innerShadowParams)
    }

    @Test fun refreshDuringDismissKeepsTheCurrentFadeInsteadOfFlashingOpaque() {
        val config = Config()
        val original = config.gradientStrokeLineParam!!
        val stroke = NativePanelStroke(config)
        stroke.restore(0.5f)
        config.nativePanelRefresh()
        stroke.restore(0.25f)
        assertEquals(0.1f, config.gradientStrokeLineParam!!.strokeLineAlphaNear, 0f)
        assertEquals(0.2f, config.gradientStrokeLineParam!!.strokeLineAlphaFar, 0f)
        assertEquals(0.4f, original.strokeLineAlphaNear, 0f)
        assertEquals(0.8f, original.strokeLineAlphaFar, 0f)
    }

    @Test fun refreshAtFadeEndStaysTransparentAndNextShowRestoresThePreset() {
        val config = Config()
        val original = config.gradientStrokeLineParam
        val stroke = NativePanelStroke(config)
        config.nativePanelRefresh()
        stroke.restore(0f)
        assertEquals(0f, config.gradientStrokeLineParam!!.strokeLineAlphaNear, 0f)
        assertEquals(0f, config.gradientStrokeLineParam!!.strokeLineAlphaFar, 0f)
        config.nativePanelRefresh()
        stroke.restore(1f)
        assertSame(original, config.gradientStrokeLineParam)
    }
}
