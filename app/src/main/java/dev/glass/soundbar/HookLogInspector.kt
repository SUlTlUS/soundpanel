package dev.glass.soundbar

import android.annotation.SuppressLint
import android.view.RoundedCorner
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeaze.tianyinwallpaper.backdrop.Backdrop
import com.zeaze.tianyinwallpaper.backdrop.backdrops.rememberLayerBackdrop
import com.zeaze.tianyinwallpaper.catalog.components.LiquidButton
import com.zeaze.tianyinwallpaper.catalog.components.LiquidButtonStyle
import dev.liquidglass.ios27.Ios27ListRow
import dev.liquidglass.ios27.Ios27ListSection
import dev.liquidglass.ios27.Ios27PageContent
import dev.liquidglass.ios27.Ios27Tokens
import dev.liquidglass.ios27.LiquidDialogLayer
import dev.liquidglass.ios27.LocalIos27PageColors
import dev.liquidglass.ios27.ios27AndroidPane

private val InspectorToolbarStyle = LiquidButtonStyle(
    height = 44.dp, horizontalPadding = 0.dp, contentSpacing = 0.dp,
    blurRadius = 6.dp, lensRadiusX = 30.dp, lensRadiusY = 21.dp,
)

/** Business content for the framework's Type=Inspector (12740-24122). */
@SuppressLint("DiscouragedApi")
@Composable
internal fun HookLogInspector(
    visible: Boolean,
    priority: Int,
    dark: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onLevelSelected: (Int) -> Unit,
) {
    if (LocalSettingsAppearance.current.native != null) {
        NativeHookLogDialog(visible, priority, onDismiss, onLevelSelected)
        return
    }
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible
    val transition = updateTransition(visibility, label = "Hook log Inspector")
    val progress by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = if (targetState) 220 else 180, easing = FastOutSlowInEasing)
        },
        label = "Inspector slide and fade",
    ) { if (it) 1f else 0f }
    val mounted = visibility.currentState || visibility.targetState
    BackHandler(enabled = mounted, onBack = onDismiss)
    if (!mounted) return

    var panelHeight by remember { mutableIntStateOf(0) }
    val colors = LocalIos27PageColors.current
    val context = LocalContext.current
    val insets = LocalView.current.rootWindowInsets
    val bottomRadii = remember(context, insets) {
        val id = context.resources.getIdentifier("rounded_corner_radius_bottom", "dimen", "android")
        val fallback = if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        fun radius(position: Int) = insets?.getRoundedCorner(position)?.radius?.takeIf { it > 0 } ?: fallback
        radius(RoundedCorner.POSITION_BOTTOM_LEFT) to radius(RoundedCorner.POSITION_BOTTOM_RIGHT)
    }
    val shape = with(LocalDensity.current) {
        AbsoluteRoundedCornerShape(32.dp, 32.dp, bottomRadii.second.toDp(), bottomRadii.first.toDp())
    }
    val surface = if (dark) Color(0xCC2C2C2E) else Color.White.copy(alpha = 0.70f)
    val sheetBackdrop = rememberLayerBackdrop()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LiquidDialogLayer(
            backdrop = backdrop,
            onDismissRequest = onDismiss,
            panelModifier = Modifier.ios27AndroidPane("Hook 日志级别", onDismiss)
                .graphicsLayer {
                    translationY = (1f - progress) * panelHeight
                    alpha = progress
                    // Keep the framework shadow outside the panel bounds during fades.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .onSizeChanged { panelHeight = it.height }
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                .widthIn(max = Ios27Tokens.ReadableWidth.dp).fillMaxWidth()
                .heightIn(max = (maxHeight - 16.dp).coerceAtLeast(1.dp)),
            alignment = Alignment.BottomCenter,
            shape = shape,
            surfaceColor = surface,
            scrimColor = Color.Transparent,
            dark = dark,
            exportedBackdrop = sheetBackdrop,
            description = "Hook 日志级别",
        ) {
            Ios27PageContent(
                Modifier.fillMaxWidth().padding(top = 76.dp, bottom = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Ios27ListSection {
                    SettingsListRows(HookLogLevel.entries) { level ->
                        val checked = HookLogLevel.fromPriority(priority) == level
                        Ios27ListRow(
                            title = level.title,
                            modifier = Modifier.semantics { selected = checked },
                            disclosure = false,
                            onClick = { onLevelSelected(level.priority) },
                            trailing = {
                                if (checked) Icon(Icons.Rounded.Check, "已选择", tint = colors.accent, modifier = Modifier.size(22.dp))
                            },
                        )
                    }
                }
            }
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = 5.dp).size(58.dp, 4.dp)
                    .clip(RoundedCornerShape(100.dp)).background(Color(0xFFCCCCCC)),
            )
            Text(
                "Hook 日志级别", color = colors.label, style = Ios27Tokens.Type.Headline,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 29.dp),
            )
            LiquidButton(
                onClick = onDismiss,
                backdrop = sheetBackdrop,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = 16.dp).size(44.dp)
                    .semantics { contentDescription = "完成" },
                tint = colors.accent,
                style = InspectorToolbarStyle,
                buttonHeight = 44.dp,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}
