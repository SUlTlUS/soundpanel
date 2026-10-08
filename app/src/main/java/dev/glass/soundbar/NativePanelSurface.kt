package dev.glass.soundbar

/** Apply a shared collapsed-slider baseline, restoring this surface's own native state on exit. */
internal class NativePanelSurface(private val config: Any) {
    private val original = NativeSurfacePreset.capture(config)
    private var preset = original

    fun setPreset(baseline: NativeSurfacePreset, amount: Float, panel: Boolean) {
        preset = baseline.toned(amount, panel)
        apply()
    }

    fun apply(): Boolean = preset.applyTo(config)

    fun restore() {
        original.applyTo(config)
    }
}
