package dev.glass.soundbar

import kotlin.math.roundToInt

/** Keep the right edge stationary without scaling the slider contents. */
internal object PanelMorphGeometry {
    fun followedLeft(width: Int, capsuleWidth: Float, translation: Float, settledTranslation: Float): Int =
        (translation - settledTranslation).roundToInt().coerceIn(0, (width - capsuleWidth).roundToInt().coerceAtLeast(0))

    fun advanceLeft(current: Int, desired: Int, entering: Boolean): Int =
        if (entering) minOf(current, desired) else maxOf(current, desired)

    fun left(width: Int, fromWidth: Float, toWidth: Float, progress: Float): Int {
        val visible = fromWidth + (toWidth - fromWidth) * progress.coerceIn(0f, 1f)
        return (width - visible.coerceIn(0f, width.toFloat())).roundToInt().coerceIn(0, width)
    }
}
