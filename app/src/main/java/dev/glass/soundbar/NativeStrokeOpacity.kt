package dev.glass.soundbar

/** Own a copy so fading one panel cannot change the ROM's shared stroke preset. */
internal class NativeStrokeOpacity(val original: Any) {
    private val fields = listOf(
        "StrokeLineColor", "StrokeLineVerticalNearSolid", "StrokeLineVerticalNearFade",
        "StrokeLineVerticalFarSolid", "StrokeLineVerticalFarFade", "StrokeLineAlphaNear",
        "StrokeLineAlphaFar", "StrokeLineTransverseNearSolid", "StrokeLineTransverseNearFade",
        "StrokeLineTransverseFarSolid", "StrokeLineTransverseFarFade", "Ratio", "StrokeLinePow", "StrokeLineMix",
    )
    private val copy = Reflect.call(original, "copy", *fields.map { Reflect.call(original, "get$it") }.toTypedArray())!!
    private val near = Reflect.call(original, "getStrokeLineAlphaNear") as Float
    private val far = Reflect.call(original, "getStrokeLineAlphaFar") as Float

    fun at(amount: Float): Any {
        val opacity = amount.coerceIn(0f, 1f)
        if (opacity == 1f) return original
        Reflect.call(copy, "setStrokeLineAlphaNear", near * opacity)
        Reflect.call(copy, "setStrokeLineAlphaFar", far * opacity)
        return copy
    }
}
