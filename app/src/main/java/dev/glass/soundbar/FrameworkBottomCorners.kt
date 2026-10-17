package dev.glass.soundbar

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Stable
internal data class SystemBottomCornerRadii(
    val start: Dp,
    val end: Dp,
)

@Composable
internal fun rememberSystemBottomCornerRadii(): SystemBottomCornerRadii {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val view = LocalView.current
    val insets = view.rootWindowInsets
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val radiiPx = remember(context, view, insets) {
        val fallback = bottomCornerRadiusFromResources(context)
        val left = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            insets
                ?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
                ?.radius
                ?.takeIf { it > 0 }
        } else {
            null
        } ?: fallback
        val right = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            insets
                ?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
                ?.radius
                ?.takeIf { it > 0 }
        } else {
            null
        } ?: fallback
        left to right
    }

    val left = (radiiPx.first / density).dp
    val right = (radiiPx.second / density).dp
    return if (rtl) {
        SystemBottomCornerRadii(start = right, end = left)
    } else {
        SystemBottomCornerRadii(start = left, end = right)
    }
}

@SuppressLint("DiscouragedApi")
private fun bottomCornerRadiusFromResources(context: Context): Int {
    val id = context.resources.getIdentifier(
        "rounded_corner_radius_bottom",
        "dimen",
        "android",
    )
    return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
}
