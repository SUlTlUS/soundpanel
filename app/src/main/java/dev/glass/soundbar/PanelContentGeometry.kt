package dev.glass.soundbar

import kotlin.math.roundToInt

/** Remove only the reserved label area; leave the native slider area unchanged. */
internal object PanelContentGeometry {
    fun topMargin(capsuleTop: Int, padding: Float, parentTop: Int): Int =
        (capsuleTop - padding - parentTop).toInt().coerceAtLeast(0)

    fun symmetricHeight(capsuleHeight: Int, padding: Float): Int =
        (capsuleHeight + padding * 2f).roundToInt()

    fun compactHeight(nativeHeight: Int, nativeLabelHeight: Int, visibleLabelHeight: Int): Int =
        nativeHeight - nativeLabelHeight.coerceIn(0, nativeHeight) + visibleLabelHeight.coerceAtLeast(0)
}
