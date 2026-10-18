package dev.glass.soundbar

import kotlin.math.roundToInt

/** Remove only the reserved label area; leave the native slider area unchanged. */
internal object PanelContentGeometry {
    data class RowWidths(val row: Int, val slider: Int)

    fun rowWidths(rowLayout: Int, rowMeasured: Int, sliderLayout: Int, sliderMeasured: Int): RowWidths? {
        val row = rowLayout.takeIf { it > 0 } ?: rowMeasured.takeIf { it > 0 } ?: return null
        val slider = sliderLayout.takeIf { it > 0 } ?: sliderMeasured.takeIf { it > 0 } ?: return null
        return if (row >= slider) RowWidths(row, slider) else null
    }

    fun topMargin(capsuleTop: Int, padding: Float, parentTop: Int): Int =
        (capsuleTop - padding - parentTop).toInt().coerceAtLeast(0)

    fun symmetricHeight(capsuleHeight: Int, padding: Float): Int =
        (capsuleHeight + padding * 2f).roundToInt()

    fun compactHeight(nativeHeight: Int, nativeLabelHeight: Int, visibleLabelHeight: Int): Int =
        nativeHeight - nativeLabelHeight.coerceIn(0, nativeHeight) + visibleLabelHeight.coerceAtLeast(0)
}
