package dev.glass.soundbar

import org.junit.Assert.*
import org.junit.Test

class NativePanelSurfaceTest {
    @Test fun expandedTrackAndFillKeepTheirSeparateCollapsedPalettes() {
        val trackMix = NativePanelToneTest.BlurMixMultiWithShader(
            NativePanelToneTest.MixColorWithShader(3, 0xFF000000.toInt(), 5, 0x4D595959),
            NativePanelToneTest.MixColorWithShader(0, 0, 0, 0), 1f, false, 0)
        val fillMix = NativePanelToneTest.BlurMixMultiWithShader(
            NativePanelToneTest.MixColorWithShader(5, 0x66FFFFFF, 3, 0xFFFFFFFF.toInt()),
            trackMix.foregroundShaderParam, 1f, false, 0)
        val track = Config().apply { blurColor = 0; platformMixConfig = trackMix }
        val fill = Config().apply { blurColor = 0; platformMixConfig = fillMix }
        val collapsedTrack = NativeSurfacePreset.capture(track)
        val collapsedFill = NativeSurfacePreset.capture(fill)
        val expandedTrack = Config()
        val expandedFill = Config()
        NativePanelSurface(expandedTrack).setPreset(collapsedTrack, -1f, panel = false)
        NativePanelSurface(expandedFill).setPreset(collapsedFill, -1f, panel = false)
        assertEquals(trackMix, expandedTrack.platformMixConfig)
        assertEquals(fillMix, expandedFill.platformMixConfig)
        assertNotEquals(expandedTrack.platformMixConfig, expandedFill.platformMixConfig)
        assertNotSame(fillMix, expandedFill.platformMixConfig)
        assertEquals(0, expandedFill.blurColor)
    }

    @Test fun progressPaletteSurvivesNativeExpandWritesAndRestoresOnExit() {
        val collapsedMix = NativePanelToneTest.BlurMixMultiWithShader(
            NativePanelToneTest.MixColorWithShader(5, 0x66FFFFFF, 3, -1),
            NativePanelToneTest.MixColorWithShader(3, 0xFF000000.toInt(), 5, 0x4D595959),
            1f, false, 0)
        val collapsed = NativeSurfacePreset.capture(Config().apply { blurColor = 0; platformMixConfig = collapsedMix })
        val expanded = Config()
        val original = NativeSurfacePreset.capture(expanded)
        val surface = NativePanelSurface(expanded)
        surface.setPreset(collapsed, 1f, panel = false)
        repeat(3) {
            // writeProgressPlatform normally changes both shader layers and fade.
            expanded.platformMixConfig = NativePanelToneTest.BlurMixMultiWithShader(
                NativePanelToneTest.MixColorWithShader(5, 0x66999999, 3, 0xFF999999.toInt()),
                NativePanelToneTest.MixColorWithShader(5, 0x66000000, 2, 0x66999999),
                1f, true, 0)
            assertTrue(surface.apply())
            assertEquals(collapsedMix, expanded.platformMixConfig)
            assertFalse(surface.apply())
        }
        surface.restore()
        assertEquals(original, NativeSurfacePreset.capture(expanded))
    }

    class Config {
        var blurRadius = 230
        var blurColor = 0x80446688.toInt()
        var platformMixConfig: Any = Any()
        val captureOwner = Any()
        var motionBlurMixConfig: Any? = Any()
        var motionBlurRadius = 0
        var platformBlurDrawableFullOpacity = false
        val geometry = Any()
    }

    class LegacyConfig {
        var blurRadius = 420
        var blurColor = 0x44335577
        var platformMixConfig: Any = Any()
        var motionBlurMixConfig: Any? = null
    }

    @Test fun legacyContractHasOneSharedBlurRadiusAndNoMotionRadiusAccessors() {
        val source = LegacyConfig()
        val baseline = NativeSurfacePreset.capture(source)
        assertNull(baseline.motionRadius)
        val target = LegacyConfig().apply { blurRadius = 250; blurColor = 0 }
        val original = NativeSurfacePreset.capture(target)
        val surface = NativePanelSurface(target)
        surface.setPreset(baseline, 0f, panel = false)
        assertEquals(baseline, NativeSurfacePreset.capture(target))
        surface.restore()
        assertEquals(original, NativeSurfacePreset.capture(target))
    }

    @Test fun panelDefaultsSlightlyDarkerWhileSliderKeepsNativeSurfaceAndFade() {
        val collapsed = Config().apply { blurRadius = 190; motionBlurRadius = 140 }
        val baseline = NativeSurfacePreset.capture(collapsed)
        val config = Config()
        val capture = config.captureOwner
        val geometry = config.geometry
        NativePanelSurface(config).setPreset(baseline, 0f, panel = true)
        val bar = Config().apply { platformBlurDrawableFullOpacity = true }
        NativePanelSurface(bar).setPreset(baseline, 0f, panel = false)
        assertEquals(0x803F5E7D.toInt(), config.blurColor)
        assertEquals(baseline.radius, config.blurRadius)
        assertSame(baseline.motionMix, config.motionBlurMixConfig)
        assertEquals(baseline, NativeSurfacePreset.capture(bar))
        assertSame(capture, config.captureOwner)
        assertSame(geometry, config.geometry)
        assertFalse(config.platformBlurDrawableFullOpacity)
        assertTrue(bar.platformBlurDrawableFullOpacity)
    }

    @Test fun nativeRefreshCannotReplaceTheSliderNativeBaseline() {
        val config = Config()
        val surface = NativePanelSurface(config)
        val baseline = NativeSurfacePreset.capture(config)
        surface.setPreset(baseline, 0.5f, panel = false)
        val tonedColor = config.blurColor
        config.blurRadius = 230
        config.blurColor = -1
        config.platformMixConfig = Any()
        assertTrue(surface.apply())
        assertEquals(tonedColor, config.blurColor)
        assertSame(baseline.platformMix, config.platformMixConfig)
        assertFalse(surface.apply())
    }

    @Test fun exitRestoresEachSurfaceOwnOriginalMaterial() {
        val config = Config()
        val nativeMix = config.platformMixConfig
        val surface = NativePanelSurface(config)
        val baseline = NativeSurfacePreset.capture(Config().apply { blurRadius = 180; motionBlurRadius = 120 })
        surface.setPreset(baseline, -0.5f, panel = false)
        surface.setPreset(baseline, 0.5f, panel = false)
        surface.restore()
        assertEquals(230, config.blurRadius)
        assertEquals(0x80446688.toInt(), config.blurColor)
        assertSame(nativeMix, config.platformMixConfig)
        assertEquals(0, config.motionBlurRadius)
    }

    @Test fun panelUsesCollapsedShaderColorsButRetainsSmoothDismissFade() {
        val mix = NativePanelToneTest.BlurMixMultiWithShader(
            NativePanelToneTest.MixColorWithShader(3, 0x55446688, 5, 0x33335577),
            NativePanelToneTest.MixColorWithShader(5, 0, 2, 0), 1f, false, 0)
        val collapsed = NativeSurfacePreset.capture(Config().apply { platformMixConfig = mix })
        val panel = Config()
        val bar = Config()
        NativePanelSurface(panel).setPreset(collapsed, 0f, panel = true)
        NativePanelSurface(bar).setPreset(collapsed, 0f, panel = false)
        val panelMix = panel.platformMixConfig as NativePanelToneTest.BlurMixMultiWithShader
        assertEquals(NativePanelToneTest.MixColorWithShader(3, 0x553F5E7D, 5, 0x332F4E6D), panelMix.foregroundShaderParam)
        assertEquals(mix.backgroundShaderParam, panelMix.backgroundShaderParam)
        assertTrue(panelMix.alphaWithBlurAmount)
        assertFalse(mix.alphaWithBlurAmount)
        assertEquals(mix, bar.platformMixConfig)
        assertNotSame(mix, bar.platformMixConfig)
    }

    @Test fun panelToneLeavesSliderNativeEvenAtBothAdjustmentEndpoints() {
        val source = Config().apply {
            platformMixConfig = NativePanelToneTest.BlurMixSingleWithShader(
                NativePanelToneTest.MixColorWithShader(5, 0x886688AA.toInt(), 2, 0x55335577))
        }
        val preset = NativeSurfacePreset.capture(source)
        val panel = Config()
        val bar = Config()
        NativePanelSurface(panel).setPreset(preset, 0.5f, panel = true)
        NativePanelSurface(bar).setPreset(preset, 0.5f, panel = false)
        assertEquals(0x8057728D.toInt(), panel.blurColor)
        assertEquals(source.blurColor, bar.blurColor)
        val panelMix = (panel.platformMixConfig as NativePanelToneTest.BlurMixSingleWithShader).backgroundShaderParam
        val barMix = (bar.platformMixConfig as NativePanelToneTest.BlurMixSingleWithShader).backgroundShaderParam
        assertEquals(0x886688AA.toInt(), barMix.topLayerColor)
        assertEquals(0x55335577, barMix.bottomLayerColor)
        assertEquals(barMix.topMode, panelMix.topMode)
        assertEquals(barMix.bottomMode, panelMix.bottomMode)
        assertEquals(barMix.topLayerColor ushr 24, panelMix.topLayerColor ushr 24)
        assertEquals(0x336F869E, NativeSurfacePreset.panelColor(0x33446688, 1f))
        assertEquals(0x332F475E, NativeSurfacePreset.panelColor(0x33446688, -1f))
        assertEquals(0x80446688.toInt(), source.blurColor)
        listOf(-1f, 1f).forEach { amount ->
            NativePanelSurface(bar).setPreset(preset, amount, panel = false)
            assertEquals(source.blurColor, bar.blurColor)
            assertEquals(source.platformMixConfig, bar.platformMixConfig)
            assertNotSame(source.platformMixConfig, bar.platformMixConfig)
        }
    }

    @Test fun panelAndEachSliderHaveIndependentMutableMixState() {
        val mix = NativePanelToneTest.BlurMixMultiWithShader(
            NativePanelToneTest.MixColorWithShader(3, 0xFF000000.toInt(), 5, 0x4D595959),
            NativePanelToneTest.MixColorWithShader(0, 0, 0, 0), 1f, false, 0)
        val preset = NativeSurfacePreset.capture(Config().apply { platformMixConfig = mix })
        val panel = Config()
        val first = Config()
        val second = Config()
        NativePanelSurface(panel).setPreset(preset, -0.5f, panel = true)
        NativePanelSurface(first).setPreset(preset, -0.5f, panel = false)
        NativePanelSurface(second).setPreset(preset, -0.5f, panel = false)
        val panelMix = panel.platformMixConfig as NativePanelToneTest.BlurMixMultiWithShader
        val firstMix = first.platformMixConfig as NativePanelToneTest.BlurMixMultiWithShader
        val secondMix = second.platformMixConfig as NativePanelToneTest.BlurMixMultiWithShader
        assertNotSame(panelMix, firstMix)
        assertNotSame(firstMix, secondMix)
        firstMix.alphaWithBlurAmount = true
        firstMix.placeHolderColor = -1
        assertFalse(secondMix.alphaWithBlurAmount)
        assertFalse(mix.alphaWithBlurAmount)
        assertEquals(0, secondMix.placeHolderColor)
        assertEquals(0, panelMix.placeHolderColor)
        assertEquals(0x4D595959, secondMix.foregroundShaderParam.bottomLayerColor)
        assertTrue((panelMix.foregroundShaderParam.bottomLayerColor and 255) < 89)
    }
}
