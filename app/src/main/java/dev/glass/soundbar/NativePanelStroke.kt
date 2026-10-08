package dev.glass.soundbar

/** Keep the expanded panel stroke across the ROM's writePanelPlatform refresh. */
internal class NativePanelStroke(private val config: Any) {
    private val radius = Reflect.call(config, "getCornerRadius") as Float
    private val corner = Reflect.call(config, "getGradientStrokeCornerParam")!!
    private val type = Reflect.call(corner, "getType")
    private val cornerRadius = Reflect.call(corner, "getRadius") as Float
    private val weight = Reflect.call(corner, "getWeight") as Float
    private val opacity = NativeStrokeOpacity(Reflect.call(config, "getGradientStrokeLineParam")!!)
    private val optics = Reflect.call(config, "getOpticsParams")
    private val shadow = Reflect.call(config, "getInnerShadowParams")

    fun restore(amount: Float) {
        Reflect.call(config, "setCornerRadius", radius)
        Reflect.call(config, "setEnableStaticBlurCorner", true)
        Reflect.call(config, "setGradientStrokeLineParam", opacity.at(amount))
        Reflect.call(config, "setOpticsParams", optics)
        Reflect.call(config, "setInnerShadowParams", shadow)
        Reflect.call(corner, "setType", type)
        Reflect.call(corner, "setRadius", cornerRadius)
        Reflect.call(corner, "setWeight", weight)
    }

    /** Apply to the native renderer that AutoBlurDrawable will actually draw. */
    fun applyTo(drawable: Any, amount: Float) {
        val shader = Reflect.call(drawable, "getDrawableShader")!!
        if (Reflect.call(drawable, "getEnableShader") != true) Reflect.call(drawable, "setEnableBlurShader", true)
        if (Reflect.call(shader, "getOpticsParams") != optics) Reflect.call(drawable, "setOpticsParams", optics)
        if (Reflect.call(shader, "getInnerShadowParams") != shadow) Reflect.call(drawable, "setInnerShadowParams", shadow)
        if (Reflect.call(shader, "getGradientStrokeLineParams") != opacity.original) {
            Reflect.call(drawable, "setGradientStrokeLineParams", opacity.original)
        }
        if (Reflect.call(shader, "getCornerParams") != corner) Reflect.call(drawable, "setCornerParams", corner)
        // Fade the whole light package, including optics and inner shadow.
        val alpha = (amount.coerceIn(0f, 1f) * 255f).toInt()
        if (Reflect.call(drawable, "getPaintAlpha") != alpha) Reflect.call(drawable, "setAlpha", alpha)
    }
}
