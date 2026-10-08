package dev.glass.soundbar

/** Only the surface colors/radii; geometry, capture and light remain owned by each native view. */
internal data class NativeSurfacePreset(
    val radius: Int,
    val color: Int,
    val platformMix: Any?,
    val motionMix: Any?,
    val motionRadius: Int?,
) {
    fun toned(amount: Float, panel: Boolean): NativeSurfacePreset {
        // Slider backgrounds keep the native palette, with their own mutable shader cache.
        if (!panel) return adjusted(0f, copyMix = true)
        return adjusted(PANEL_DEFAULT_SHADE).adjusted(panelAdjustment(amount), true)
    }

    private fun adjusted(amount: Float, fade: Boolean? = null, copyMix: Boolean = false): NativeSurfacePreset = copy(
        color = NativePanelTone.adjustColor(color, amount),
        platformMix = NativePanelTone.adjustMix(platformMix, amount, fade, copyMix),
        motionMix = NativePanelTone.adjustMix(motionMix, amount, copy = copyMix),
    )

    fun applyTo(config: Any): Boolean {
        val changed = Reflect.call(config, "getBlurRadius") != radius ||
            Reflect.call(config, "getBlurColor") != color ||
            Reflect.call(config, "getPlatformMixConfig") !== platformMix ||
            Reflect.call(config, "getMotionBlurMixConfig") !== motionMix ||
            (motionRadius != null && Reflect.call(config, "getMotionBlurRadius") != motionRadius)
        if (!changed) return false
        Reflect.call(config, "setBlurRadius", radius)
        Reflect.call(config, "setBlurColor", color)
        Reflect.call(config, "setPlatformMixConfig", platformMix)
        Reflect.call(config, "setMotionBlurMixConfig", motionMix)
        if (motionRadius != null) Reflect.call(config, "setMotionBlurRadius", motionRadius)
        return true
    }

    companion object {
        private const val PANEL_DEFAULT_SHADE = -0.08f
        private const val PANEL_ADJUSTMENT_RANGE = 0.25f

        fun panelAdjustment(amount: Float): Float = amount.coerceIn(-1f, 1f) * PANEL_ADJUSTMENT_RANGE

        fun panelColor(color: Int, amount: Float): Int =
            NativePanelTone.adjustColor(NativePanelTone.adjustColor(color, PANEL_DEFAULT_SHADE), panelAdjustment(amount))

        fun capture(config: Any) = NativeSurfacePreset(
            Reflect.call(config, "getBlurRadius") as Int,
            Reflect.call(config, "getBlurColor") as Int,
            Reflect.call(config, "getPlatformMixConfig"),
            Reflect.call(config, "getMotionBlurMixConfig"),
            // Ace 6's BlurConfig uses blurRadius for motion too; only the newer contract splits it.
            config.javaClass.methods.firstOrNull { it.name == "getMotionBlurRadius" && it.parameterCount == 0 }
                ?.invoke(config) as? Int,
        )
    }
}
