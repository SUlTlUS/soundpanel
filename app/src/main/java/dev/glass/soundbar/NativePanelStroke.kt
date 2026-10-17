package dev.glass.soundbar

/** Keep the expanded panel stroke across the ROM's writePanelPlatform refresh. */
internal class NativePanelStroke(private val config: Any, private val highlightEnabled: Boolean = true) {
    private val radius = Reflect.call(config, "getCornerRadius") as Float
    private val corner = Reflect.call(config, "getGradientStrokeCornerParam")!!
    private val type = Reflect.call(corner, "getType")
    private val cornerRadius = Reflect.call(corner, "getRadius") as Float
    private val weight = Reflect.call(corner, "getWeight") as Float
    private val opacity = Reflect.call(config, "getGradientStrokeLineParam")?.let(::NativeStrokeOpacity)
    private val optics = Reflect.call(config, "getOpticsParams")
    private val shadow = Reflect.call(config, "getInnerShadowParams")

    fun restore(amount: Float) {
        Reflect.call(config, "setCornerRadius", radius)
        Reflect.call(config, "setEnableStaticBlurCorner", true)
        Reflect.call(config, "setGradientStrokeLineParam", opacity?.at(if (highlightEnabled) amount else 0f))
        Reflect.call(config, "setOpticsParams", optics)
        Reflect.call(config, "setInnerShadowParams", shadow)
        restoreCorner()
    }

    private fun restoreCorner() {
        // SystemUI refreshes the same mutable corner object in place.
        if (Reflect.call(corner, "getType") != type) Reflect.call(corner, "setType", type)
        if (Reflect.call(corner, "getRadius") != cornerRadius) Reflect.call(corner, "setRadius", cornerRadius)
        if (Reflect.call(corner, "getWeight") != weight) Reflect.call(corner, "setWeight", weight)
    }

    /** Apply to the native renderer that AutoBlurDrawable will actually draw. */
    fun applyTo(drawable: Any, amount: Float) {
        restoreCorner()
        val shader = Reflect.call(drawable, "getDrawableShader")!!
        if (Reflect.call(drawable, "getEnableShader") != true) Reflect.call(drawable, "setEnableBlurShader", true)
        if (Reflect.call(shader, "getOpticsParams") != optics) Reflect.call(drawable, "setOpticsParams", optics)
        if (Reflect.call(shader, "getInnerShadowParams") != shadow) Reflect.call(drawable, "setInnerShadowParams", shadow)
        val line = if (highlightEnabled) opacity?.original else opacity?.at(0f)
        if (Reflect.call(shader, "getGradientStrokeLineParams") != line) {
            Reflect.call(drawable, "setGradientStrokeLineParams", line)
        }
        if (Reflect.call(shader, "getCornerParams") != corner) Reflect.call(drawable, "setCornerParams", corner)
        // Fade the whole light package, including optics and inner shadow.
        val alpha = (amount.coerceIn(0f, 1f) * 255f).toInt()
        if (Reflect.call(drawable, "getPaintAlpha") != alpha) Reflect.call(drawable, "setAlpha", alpha)
    }
}
