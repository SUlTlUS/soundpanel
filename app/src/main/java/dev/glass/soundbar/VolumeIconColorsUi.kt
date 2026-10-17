package dev.glass.soundbar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeaze.tianyinwallpaper.backdrop.Backdrop
import dev.liquidglass.ios27.*

@Composable
internal fun VolumeIconColorSection(
    settings: PanelSettings, onReset: () -> Unit, onSelect: (VolumeIconStream) -> Unit,
) {
    val colors = LocalIos27PageColors.current
    Ios27ListSection(title = "音量图标颜色") {
        // Reset belongs to the app settings; the framework picker has its original toolbar.
        SettingsListRows(VolumeIconStream.entries.toList() + listOf(null)) { stream ->
            if (stream == null) {
                Ios27ListRow(title = "还原系统颜色", onClick = onReset)
            } else {
                val color = stream.color(settings)
                Ios27ListRow(title = stream.title, onClick = { onSelect(stream) }, trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (color == null) "系统默认" else "#%08X".format(color),
                            color = colors.secondaryLabel, style = Ios27Tokens.Type.Body)
                        Box(Modifier.size(22.dp).background(color?.let(::Color) ?: colors.label, CircleShape)
                            .border(1.dp, colors.separator, CircleShape)
                            .semantics { contentDescription = if (color == null) "系统默认颜色" else "已选颜色" })
                    }
                })
            }
        }
    }
}

/** Only application state and the requested transition; all panel UI stays in the framework port. */
@Composable
internal fun VolumeIconColorPicker(
    visible: Boolean,
    stream: VolumeIconStream,
    initialColor: Int?,
    dark: Boolean,
    backdrop: Backdrop,
    onSave: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val seed = rememberSaveable { initialColor ?: 0xFFAF52DE.toInt() }
    var draft by rememberSaveable { mutableIntStateOf(seed) }
    var grid by rememberSaveable { mutableIntStateOf(initialColor?.let { colorPickerGridIndex(Color(it)) } ?: 75) }
    var swatch by rememberSaveable { mutableIntStateOf(if (initialColor == null) 6 else -1) }
    var opacity by rememberSaveable { mutableFloatStateOf(Color(seed).alpha) }
    var panelHeight by remember { mutableIntStateOf(0) }
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible
    val transition = rememberTransition(visibility, label = "Icon color picker")
    val progress by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 240 else 180, easing = FastOutSlowInEasing) },
        label = "Picker slide and fade",
    ) { if (it) 1f else 0f }
    val mounted = visibility.currentState || visibility.targetState || !visibility.isIdle
    val dismiss = {
        if (visible) {
            if (draft != seed) onSave(draft)
            onDismiss()
        }
    }
    BackHandler(enabled = mounted, onBack = dismiss)
    if (!mounted) return
    Ios27ColorPickerDialog(
        file = "12740-35184.json", backdrop = backdrop, colors = LocalIos27PageColors.current, dark = dark,
        selectedGridIndex = grid, selectedSwatchIndex = swatch, opacity = opacity,
        onGridSelected = { if (visible) { grid = it; swatch = -1 } },
        onSwatchSelected = { if (visible) { swatch = it } },
        onOpacityChange = { if (visible) opacity = it },
        initialColor = initialColor?.let(::Color),
        onColorChange = { if (visible) draft = it.toArgb() },
        panelModifier = Modifier.graphicsLayer {
            translationY = (1f - progress) * panelHeight
            alpha = progress
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }.onSizeChanged { panelHeight = it.height },
        onDismiss = dismiss,
    )
}
