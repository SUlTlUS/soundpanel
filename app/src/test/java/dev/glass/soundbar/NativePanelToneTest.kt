package dev.glass.soundbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePanelToneTest {
    // Contract fixtures from the two supplied SystemUI APKs.
    open class MixFlags {
        var alphaWithBlurAmount = true
        var mirrorScale = 0.8f
        var placeHolderColor = 0x55446688
    }
    data class MixColor(val mode: Int, val topLayerColor: Int, val bottomLayerColor: Int)
    data class MixColorWithShader(val topMode: Int, val topLayerColor: Int, val bottomMode: Int, val bottomLayerColor: Int)
    data class BlurMixSingle(val mixColor: MixColor) : MixFlags()
    data class BlurMixSingleWithShader(val backgroundShaderParam: MixColorWithShader) : MixFlags()
    data class RuntimeShaderMix(val backgroundShaderParam: MixColorWithShader) : MixFlags()
    data class RuntimeMotionMix(val mixColor: MixColor) : MixFlags()
    data class BlurMixMultiWithShader(val foregroundShaderParam: MixColorWithShader,
        val backgroundShaderParam: MixColorWithShader, var mirrorScale: Float,
        var alphaWithBlurAmount: Boolean, var placeHolderColor: Int)
    // Like the real BlurConfig, hashCode depends on mutable mix/color fields.
    data class Config(var blurColor: Int, var platformMixConfig: Any, var motionBlurMixConfig: Any?)

    @Test fun qsButtonToneAdjustsBothShaderLayersWithoutLosingModesOrFade() {
        val original = BlurMixMultiWithShader(
            MixColorWithShader(5, 0x19404040, 3, 0x4D737373),
            MixColorWithShader(5, 0x92000000.toInt(), 2, 0x66999999), 1f, true, 0x33446688)
        val config = Config(0, original, null)
        NativePanelTone.apply(config, 1f)
        val light = config.platformMixConfig as BlurMixMultiWithShader
        assertEquals(MixColorWithShader(5, 0x19FFFFFF, 3, 0x4DFFFFFF), light.foregroundShaderParam)
        assertEquals(MixColorWithShader(5, 0x92FFFFFF.toInt(), 2, 0x66FFFFFF), light.backgroundShaderParam)
        assertEquals(1f, light.mirrorScale, 0f)
        assertTrue(light.alphaWithBlurAmount)
        assertEquals(0x33FFFFFF, light.placeHolderColor)
        NativePanelTone.apply(config, -1f)
        val dark = config.platformMixConfig as BlurMixMultiWithShader
        assertEquals(0x19000000, dark.foregroundShaderParam.topLayerColor)
        assertEquals(0x66000000, dark.backgroundShaderParam.bottomLayerColor)
        NativePanelTone.restore(config)
        assertSame(original, config.platformMixConfig)
    }

    @Test fun endpointsKeepNativeTransparency() {
        assertEquals(0x80FFFFFF.toInt(), NativePanelTone.adjustColor(0x806688AA.toInt(), 1f))
        assertEquals(0x80000000.toInt(), NativePanelTone.adjustColor(0x806688AA.toInt(), -1f))
        assertEquals(0x006688AA, NativePanelTone.adjustColor(0x006688AA, 1f))
        assertEquals(0x806688AA.toInt(), NativePanelTone.adjustColor(0x806688AA.toInt(), 0f))
    }

    @Test fun legacyMixRetainsModeAndRestoresOriginalObjects() {
        val original = BlurMixSingle(MixColor(4, 0x886688AA.toInt(), 0x55335577))
        val config = Config(0x776688AA, Any(), original)
        val platform = config.platformMixConfig
        NativePanelTone.apply(config, 0.5f)
        val first = config.motionBlurMixConfig as BlurMixSingle
        assertEquals(4, first.mixColor.mode)
        assertEquals(0x88B3C4D5.toInt(), first.mixColor.topLayerColor)
        assertEquals(original.alphaWithBlurAmount, first.alphaWithBlurAmount)
        assertEquals(original.mirrorScale, first.mirrorScale, 0f)
        NativePanelTone.apply(config, 0.5f)
        assertEquals(first.mixColor, (config.motionBlurMixConfig as BlurMixSingle).mixColor)
        NativePanelTone.restore(config)
        assertSame(original, config.motionBlurMixConfig)
        assertSame(platform, config.platformMixConfig)
        assertEquals(0x776688AA, config.blurColor)
    }

    @Test fun platformShaderRetainsBothModesAndReturnsToDefault() {
        val original = BlurMixSingleWithShader(MixColorWithShader(5, 0x806688AA.toInt(), 2, 0x55335577))
        val config = Config(0, original, null)
        NativePanelTone.apply(config, -1f)
        val adjusted = config.platformMixConfig as BlurMixSingleWithShader
        assertEquals(5, adjusted.backgroundShaderParam.topMode)
        assertEquals(2, adjusted.backgroundShaderParam.bottomMode)
        assertEquals(0x80000000.toInt(), adjusted.backgroundShaderParam.topLayerColor)
        assertEquals(0x55000000, adjusted.backgroundShaderParam.bottomLayerColor)
        assertEquals(original.alphaWithBlurAmount, adjusted.alphaWithBlurAmount)
        assertEquals(original.mirrorScale, adjusted.mirrorScale, 0f)
        NativePanelTone.apply(config, 0f)
        assertSame(original, config.platformMixConfig)
        NativePanelTone.restore(config)
        assertSame(original, config.platformMixConfig)
    }

    @Test fun runtimeClassNamesDoNotPreventEitherMixContract() {
        val shader = RuntimeShaderMix(MixColorWithShader(5, 0x92000000.toInt(), 2, 0x66999999))
        val motion = RuntimeMotionMix(MixColor(4, 0x80335577.toInt(), 0x55446688))
        val config = Config(0, shader, motion)
        NativePanelTone.apply(config, 1f)
        val adjustedShader = config.platformMixConfig as RuntimeShaderMix
        val adjustedMotion = config.motionBlurMixConfig as RuntimeMotionMix
        assertEquals(0x92FFFFFF.toInt(), adjustedShader.backgroundShaderParam.topLayerColor)
        assertEquals(0x66FFFFFF, adjustedShader.backgroundShaderParam.bottomLayerColor)
        assertEquals(0x80FFFFFF.toInt(), adjustedMotion.mixColor.topLayerColor)
        NativePanelTone.apply(config, -1f)
        assertEquals(0x66000000, (config.platformMixConfig as RuntimeShaderMix).backgroundShaderParam.bottomLayerColor)
        NativePanelTone.restore(config)
        assertSame(shader, config.platformMixConfig)
        assertSame(motion, config.motionBlurMixConfig)
    }
}
