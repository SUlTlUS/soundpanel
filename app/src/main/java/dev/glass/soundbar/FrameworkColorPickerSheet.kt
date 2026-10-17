package dev.glass.soundbar

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeaze.tianyinwallpaper.backdrop.Backdrop
import com.zeaze.tianyinwallpaper.backdrop.backdrops.LayerBackdrop
import dev.liquidglass.ios27.LiquidDialogLayer
import dev.liquidglass.ios27.ios27AndroidPane

/**
 * Shared iOS 27 bottom-sheet shell built on the exact LiquidDialog hierarchy:
 * Root -> Scrim -> Panel glass -> Content.
 *
 * Color Picker, Fullscreen and Inspector all reuse this component directly.
 */
@Composable
internal fun Ios27BottomSheetDialog(
    backdrop: Backdrop?,
    dark: Boolean,
    height: Dp,
    scrimColor: Color,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    panelModifier: Modifier = Modifier,
    exportedBackdrop: LayerBackdrop? = null,
    description: String = "颜色选择器弹窗",
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalSettingsAppearance.current.liquidGlass
    val bottomCorners = rememberSystemBottomCornerRadii()
    val shape = RoundedCornerShape(
        topStart = 32.dp,
        topEnd = 32.dp,
        bottomStart = bottomCorners.start,
        bottomEnd = bottomCorners.end,
    )
    val surface = if (glass) {
        if (dark) Color(0xCC2C2C2E) else Color.White.copy(alpha = 0.70f)
    } else if (dark) Color(0xFF2C2C2E) else Color.White

    LiquidDialogLayer(
        backdrop = if (glass) backdrop else null,
        onDismissRequest = onDismissRequest,
        panelModifier = panelModifier.ios27AndroidPane(description, onDismissRequest)
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(height),
        alignment = Alignment.BottomCenter,
        shape = shape,
        surfaceColor = surface,
        fallbackSurface = surface,
        scrimColor = scrimColor,
        dark = dark,
        exportedBackdrop = exportedBackdrop,
        modifier = modifier,
        description = description,
        content = content,
    )
}
