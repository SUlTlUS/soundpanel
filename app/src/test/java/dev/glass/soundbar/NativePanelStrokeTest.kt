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

    // The ROM exposes material state on a nested shader, not on the renderer.
    class Shader {
        var stroke: NativeStrokeOpacityTest.Stroke? = null
        var corner: Corner? = null
        var optics: String? = null
        var shadow: String? = null
        fun getGradientStrokeLineParams() = stroke
        fun getCornerParams() = corner
        fun getOpticsParams() = optics
        fun getInnerShadowParams() = shadow
    }

    class Renderer {
        val shader = Shader()
        var shaderEnabled = false
        var paintOpacity = 0
        fun getDrawableShader() = shader
        fun getEnableShader() = shaderEnabled
        fun getPaintAlpha() = paintOpacity
        fun setEnableBlurShader(enabled: Boolean) { shaderEnabled = enabled }
        fun setOpticsParams(value: String?) { shader.optics = value }
        fun setInnerShadowParams(value: String?) { shader.shadow = value }
        fun setAlpha(value: Int) { paintOpacity = value }
        fun setGradientStrokeLineParams(value: NativeStrokeOpacityTest.Stroke?): Boolean {
            val changed = shader.stroke != value
            shader.stroke = value
            return changed
        }
        fun setCornerParams(value: Corner): Boolean {
            val changed = shader.corner != value
            shader.corner = value.copy()
            return changed
        }
    }

    @Test fun actualRendererReceivesNativeShaderStrokeAndCorners() {
        val config = Config()
        val original = config.gradientStrokeLineParam
        val stroke = NativePanelStroke(config)
        val renderer = Renderer()
        config.nativePanelRefresh()
        stroke.applyTo(renderer, 1f)
        assertTrue(renderer.shaderEnabled)
        assertSame(original, renderer.shader.stroke)
        assertEquals(Corner(), renderer.shader.corner)
        assertEquals("native-optics", renderer.shader.optics)
        assertEquals("native-shadow", renderer.shader.shadow)
        assertEquals(255, renderer.paintOpacity)
        // A later ROM refresh mutates the shared corner object again.
        config.nativePanelRefresh()
        stroke.applyTo(renderer, 1f)
        assertSame(original, renderer.shader.stroke)
        assertEquals(Corner(), renderer.shader.corner)
        assertEquals(255, renderer.paintOpacity)
    }

    @Test fun disabledPanelLightClearsCachedRendererAndSurvivesNativeRefresh() {
        val config = Config().apply {
            gradientStrokeLineParam = null
            opticsParams = null
            innerShadowParams = null
        }
        val stroke = NativePanelStroke(config, highlightEnabled = false)
        val renderer = Renderer().apply {
            shader.stroke = NativeStrokeOpacityTest.Stroke()
            shader.optics = "cached-optics"
            shader.shadow = "cached-shadow"
        }
        config.nativePanelRefresh()
        stroke.restore(1f)
        stroke.applyTo(renderer, 1f)
        assertNull(config.gradientStrokeLineParam)
        assertNull(config.opticsParams)
        assertNull(config.innerShadowParams)
        assertNull(renderer.shader.stroke)
        assertNull(renderer.shader.optics)
        assertNull(renderer.shader.shadow)
        assertEquals(255, renderer.paintOpacity)
        assertEquals(Corner(), renderer.shader.corner)
        config.nativePanelRefresh()
        stroke.restore(0.5f)
        stroke.applyTo(renderer, 0.5f)
        assertNull(renderer.shader.stroke)
        assertNull(renderer.shader.optics)
        assertNull(renderer.shader.shadow)
    }

    @Test fun actualRendererReceivesCurrentDismissOpacity() {
        val stroke = NativePanelStroke(Config())
        val renderer = Renderer()
        stroke.applyTo(renderer, 0.25f)
        // The native renderer fades the entire light package via paint alpha;
        // stroke colors remain native instead of being faded twice.
        assertEquals(0.4f, renderer.shader.stroke!!.strokeLineAlphaNear, 0f)
        assertEquals(0.8f, renderer.shader.stroke!!.strokeLineAlphaFar, 0f)
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
