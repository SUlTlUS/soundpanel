package dev.glass.soundbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import dev.liquidglass.ios27.ios27AndroidProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeaze.tianyinwallpaper.backdrop.Backdrop
import dev.liquidglass.ios27.Ios27PageColors
import dev.liquidglass.ios27.Ios27Tokens
import dev.liquidglass.ios27.ios27PressedClickable
import kotlin.math.roundToInt

private const val PickerGridColumns = 12
private const val PickerGridRows = 10
// The picker uses the platform system sans face. These sizes follow the source
// text boxes instead of reusing the page's larger navigation type roles.
private val PickerTitleStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 17.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp,
    fontWeight = FontWeight.Normal,
)
private val PickerSegmentStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 13.333.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
    fontWeight = FontWeight.Normal,
)
private val PickerOpacityStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 13.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
    fontWeight = FontWeight.Medium,
)
private val PickerValueStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 17.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp,
    fontWeight = FontWeight.Normal,
)

// Exact Figma swatch colors. Keep the grid as data rather than deriving it from HSL:
// the source uses hand-tuned RGB stops and the small differences are visible at 1:1.
private val PickerGridColors = listOf(
    0xFFFEFFFE, 0xFFEBEBEB, 0xFFD6D6D6, 0xFFC2C2C2, 0xFFADADAD, 0xFF999999,
    0xFF858585, 0xFF707070, 0xFF5C5C5C, 0xFF474747, 0xFF333333, 0xFF000000,
    0xFF00374A, 0xFF011D57, 0xFF11053B, 0xFF2E063D, 0xFF3C071B, 0xFF5C0701,
    0xFF5A1C00, 0xFF583300, 0xFF563D00, 0xFF666100, 0xFF4F5504, 0xFF263E0F,
    0xFF004D65, 0xFF012F7B, 0xFF1A0A52, 0xFF450D59, 0xFF551029, 0xFF831100,
    0xFF7B2900, 0xFF7A4A00, 0xFF785800, 0xFF8D8602, 0xFF6F760A, 0xFF38571A,
    0xFF016E8F, 0xFF0042A9, 0xFF2C0977, 0xFF61187C, 0xFF791A3D, 0xFFB51A00,
    0xFFAD3E00, 0xFFA96800, 0xFFA67B01, 0xFFC4BC00, 0xFF9BA50E, 0xFF4E7A27,
    0xFF008CB4, 0xFF0056D6, 0xFF371A94, 0xFF7A219E, 0xFF99244F, 0xFFE22400,
    0xFFDA5100, 0xFFD38301, 0xFFD19D01, 0xFFF5EC00, 0xFFC3D117, 0xFF669D34,
    0xFF00A1D8, 0xFF0061FD, 0xFF4D22B2, 0xFF982ABC, 0xFFB92D5D, 0xFFFF4015,
    0xFFFF6A00, 0xFFFFAB01, 0xFFFCC700, 0xFFFEFB41, 0xFFD9EC37, 0xFF76BB40,
    0xFF01C7FC, 0xFF3A87FD, 0xFF5E30EB, 0xFFBE38F3, 0xFFE63B7A, 0xFFFE6250,
    0xFFFE8648, 0xFFFEB43F, 0xFFFECB3E, 0xFFFFF76B, 0xFFE4EF65, 0xFF96D35F,
    0xFF52D6FC, 0xFF74A7FF, 0xFF864FFD, 0xFFD357FE, 0xFFEE719E, 0xFFFF8C82,
    0xFFFEA57D, 0xFFFEC777, 0xFFFED977, 0xFFFFF994, 0xFFEAF28F, 0xFFB1DD8B,
    0xFF93E3FC, 0xFFA7C6FF, 0xFFB18CFE, 0xFFE292FE, 0xFFF4A4C0, 0xFFFFB5AF,
    0xFFFFC5AB, 0xFFFED9A8, 0xFFFDE4A8, 0xFFFFFBB9, 0xFFF1F7B7, 0xFFCDE8B5,
    0xFFCBF0FF, 0xFFD2E2FE, 0xFFD8C9FE, 0xFFEFCAFE, 0xFFF9D3E0, 0xFFFFDAD8,
    0xFFFFE2D6, 0xFFFEECD4, 0xFFFEF1D5, 0xFFFDFBDD, 0xFFF6FADB, 0xFFDEEED4,
).map { Color(it) }

internal fun colorPickerGridColor(index: Int): Color {
    return PickerGridColors[index.coerceIn(0, PickerGridColors.lastIndex)]
}

internal fun colorPickerGridIndex(color: Color): Int = PickerGridColors.indexOf(color.copy(alpha = 1f))

private fun pickerSwatches(dark: Boolean): List<Color> = if (dark) {
    listOf(
        Color(0xFF30D158),
        Color(0xFFFFD600),
        Color(0xFFFF9230),
        Color(0xFFFF4245),
        Color(0xFFDB34F2),
        Color(0xFF0091FF),
        Color(0xFF6D7CFF),
        Color(0xFFFF375F),
        Color(0xFFB78A66),
    )
} else {
    listOf(
        Color(0xFF34C759),
        Color(0xFFFFCC00),
        Color(0xFFFF8D28),
        Color(0xFFFF383C),
        Color(0xFFCB30E0),
        Color(0xFF0088FF),
        Color(0xFF6155F5),
        Color(0xFFFF2D55),
        Color(0xFFAC7F5E),
    )
}

private val PickerDefaultColor = Color(0xFFAF52DE)

@Composable
private fun PickerGrid(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(296.dp), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier.widthIn(max = 296.dp * PickerGridColumns / PickerGridRows).fillMaxWidth()
            .aspectRatio(PickerGridColumns.toFloat() / PickerGridRows)
            .clip(RoundedCornerShape(10.dp))
            .semantics { collectionInfo = CollectionInfo(PickerGridRows, PickerGridColumns) },
    ) {
        repeat(PickerGridRows) { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                repeat(PickerGridColumns) { column ->
                    val index = row * PickerGridColumns + column
                    Box(
                        Modifier.weight(1f).fillMaxSize()
                            .background(colorPickerGridColor(index))
                            .ios27PressedClickable(
                                shape = RoundedCornerShape(0.dp),
                                role = Role.Button,
                                onClick = { onSelected(index) },
                            )
                            .semantics {
                                contentDescription = "颜色网格 ${row + 1}-${column + 1}"
                                role = Role.RadioButton
                                selected = selectedIndex == index
                                collectionItemInfo = CollectionItemInfo(row, 1, column, 1)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selectedIndex == index) {
                            Box(
                                Modifier.widthIn(max = 29.5.dp).aspectRatio(1f)
                                    .border(3.dp, Color.White, RoundedCornerShape(0.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

private fun colorToHsv(color: Color): FloatArray = FloatArray(3).also {
    android.graphics.Color.colorToHSV(color.toArgb(), it)
}

private fun spectrumColor(x: Float, y: Float): Color {
    val hue = (y.coerceIn(0f, 1f) * 360f) % 360f
    val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val clampedX = x.coerceIn(0f, 1f)
    return if (clampedX <= 0.5f) {
        lerp(Color.White, pure, clampedX * 2f)
    } else {
        lerp(pure, Color.Black, (clampedX - 0.5f) * 2f)
    }
}

private fun spectrumPosition(color: Color): Pair<Float, Float> {
    val hsv = colorToHsv(color)
    val x = if (hsv[1] < 0.999f) hsv[1] * 0.5f else 0.5f + (1f - hsv[2]) * 0.5f
    return x.coerceIn(0f, 1f) to (hsv[0] / 360f).coerceIn(0f, 1f)
}

@Composable
private fun PickerSpectrum(
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    val marker = spectrumPosition(color)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(296.dp)
            .clip(RoundedCornerShape(10.dp))
            .semantics {
                contentDescription = "颜色光谱"
                customActions = listOf(
                    CustomAccessibilityAction("色相增加") { onColorChange(spectrumColor(marker.first, marker.second + 0.025f)); true },
                    CustomAccessibilityAction("色相减少") { onColorChange(spectrumColor(marker.first, marker.second - 0.025f)); true },
                    CustomAccessibilityAction("向黑色调整") { onColorChange(spectrumColor(marker.first + 0.05f, marker.second)); true },
                    CustomAccessibilityAction("向白色调整") { onColorChange(spectrumColor(marker.first - 0.05f, marker.second)); true },
                )
            }
            .pointerInput(Unit) {
                fun update(point: Offset) {
                    onColorChange(
                        spectrumColor(
                            point.x / size.width.coerceAtLeast(1),
                            point.y / size.height.coerceAtLeast(1),
                        )
                    )
                }
                detectTapGestures { update(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onColorChange(
                        spectrumColor(
                            change.position.x / size.width.coerceAtLeast(1),
                            change.position.y / size.height.coerceAtLeast(1),
                        )
                    )
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strips = 96
            val stripHeight = size.height / strips
            repeat(strips) { index ->
                val y = index.toFloat() / (strips - 1)
                val hue = (y * 360f) % 360f
                val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.White,
                        0.5f to pure,
                        1f to Color.Black,
                    ),
                    topLeft = Offset(0f, index * stripHeight),
                    size = androidx.compose.ui.geometry.Size(size.width, stripHeight + 1f),
                )
            }
        }
        Box(
            Modifier.offset(
                x = maxWidth * marker.first - 14.dp,
                y = maxHeight * marker.second - 14.dp,
            )
                .size(28.dp)
                .border(3.dp, Color.White, CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.28f), CircleShape),
        )
    }
}

@Composable
private fun PickerRgbSlider(
    label: String,
    channel: Int,
    color: Color,
    colors: Ios27PageColors,
    dark: Boolean,
    onColorChange: (Color) -> Unit,
) {
    val values = floatArrayOf(color.red, color.green, color.blue)
    val currentColor = rememberUpdatedState(color)
    val value = values[channel].coerceIn(0f, 1f)
    val start = values.copyOf().also { it[channel] = 0f }
    val end = values.copyOf().also { it[channel] = 1f }
    val startColor = Color(start[0], start[1], start[2], 1f)
    val endColor = Color(end[0], end[1], end[2], 1f)

    Column(Modifier.fillMaxWidth().height(61.dp)) {
        Text(
            label,
            style = PickerOpacityStyle,
            color = colors.label,
            modifier = Modifier.height(20.dp),
        )
        Row(
            Modifier.fillMaxWidth().height(34.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            BoxWithConstraints(
                Modifier.weight(1f).height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Brush.horizontalGradient(listOf(startColor, endColor)))
                    .ios27AndroidProgress(label = label, value = value, steps = 254,
                        valueDescription = (value * 255f).roundToInt().toString()) {
                        onColorChange(androidColorChannel(currentColor.value, channel, it))
                    }
                    .pointerInput(channel) {
                        fun update(x: Float) {
                            val latest = currentColor.value
                            val next = floatArrayOf(latest.red, latest.green, latest.blue)
                            next[channel] = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            onColorChange(Color(next[0], next[1], next[2], 1f))
                        }
                        detectTapGestures { update(it.x) }
                    }
                    .pointerInput(channel) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val latest = currentColor.value
                            val next = floatArrayOf(latest.red, latest.green, latest.blue)
                            next[channel] =
                                (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            onColorChange(Color(next[0], next[1], next[2], 1f))
                        }
                    },
            ) {
                val thumbX = (maxWidth - 34.dp) * value
                Box(
                    Modifier.offset(x = thumbX).size(34.dp)
                        .border(3.dp, Color.White, CircleShape)
                        .background(color, CircleShape),
                )
            }
            Box(
                Modifier.width(75.dp).height(34.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (dark) Color.Black else colors.group),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (value * 255f).roundToInt().toString(),
                    style = PickerValueStyle,
                    color = colors.label,
                )
            }
        }
    }
}

@Composable
private fun PickerSliders(
    color: Color,
    colors: Ios27PageColors,
    dark: Boolean,
    onColorChange: (Color) -> Unit,
) {
    Column(Modifier.fillMaxWidth().height(296.dp)) {
        PickerRgbSlider("红色", 0, color, colors, dark, onColorChange)
        Spacer(Modifier.height(6.dp))
        PickerRgbSlider("绿色", 1, color, colors, dark, onColorChange)
        Spacer(Modifier.height(6.dp))
        PickerRgbSlider("蓝色", 2, color, colors, dark, onColorChange)
        Spacer(Modifier.height(12.dp))
        val hex = "#%02X%02X%02X".format(
            (color.red * 255f).roundToInt(),
            (color.green * 255f).roundToInt(),
            (color.blue * 255f).roundToInt(),
        )
        Row(
            Modifier.fillMaxWidth().height(34.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "十六进制颜色 #",
                style = PickerSegmentStyle,
                color = colors.accent,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.width(92.dp).height(34.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (dark) Color.Black else colors.group),
                contentAlignment = Alignment.Center,
            ) {
                Text(hex.removePrefix("#"), style = PickerValueStyle, color = colors.label)
            }
        }
    }
}

@Composable
private fun OpacityTrack(
    color: Color,
    opacity: Float,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onOpacityChange: (Float) -> Unit,
) {
    BoxWithConstraints(
        modifier.fillMaxWidth().height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .ios27AndroidProgress(label = "不透明度", value = opacity, onValueChange = onOpacityChange)
            .drawBehind {
                val tile = 12.dp.toPx()
                val columns = (size.width / tile).toInt() + 1
                val rows = (size.height / tile).toInt() + 1
                // The source checkerboard is black + transparent. The empty cells
                // intentionally reveal the sheet underneath; they are not white.
                repeat(rows) { row ->
                    repeat(columns) { column ->
                        if ((row + column) % 2 != 0) {
                            drawRect(
                                Color.Black,
                                topLeft = androidx.compose.ui.geometry.Offset(column * tile, row * tile),
                                size = androidx.compose.ui.geometry.Size(tile, tile),
                            )
                        }
                    }
                }
                drawRect(Brush.horizontalGradient(0.12f to color.copy(alpha = 0f), 0.8798077f to color))
            }
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    val radius = 17.dp.toPx()
                    onOpacityChange(((point.x - radius) / (size.width - radius * 2).coerceAtLeast(1f)).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val radius = 17.dp.toPx()
                    onOpacityChange(((change.position.x - radius) / (size.width - radius * 2).coerceAtLeast(1f)).coerceIn(0f, 1f))
                }
            },
    ) {
        val thumbX = (maxWidth - 34.dp) * opacity.coerceIn(0f, 1f)
        Box(
            Modifier.offset(x = thumbX).size(34.dp)
                .border(3.dp, Color.White, CircleShape)
                .background(color, CircleShape),
        )
    }
}

@Composable
private fun PickerSwatch(
    color: Color,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(30.dp).background(color, CircleShape)
            .ios27PressedClickable(
                shape = CircleShape,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label; role = Role.RadioButton; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(22.dp).border(2.dp, Color.White, CircleShape))
    }
}

/**
 * iOS 27 Color Picker: a medium-detent sheet over a full-screen scrim.
 * The panel consumes the page LayerBackdrop as a sibling, so it cannot recursively sample itself.
 */
@Composable
internal fun Ios27ColorPickerDialog(
    file: String,
    backdrop: Backdrop?,
    colors: Ios27PageColors,
    dark: Boolean,
    selectedGridIndex: Int,
    selectedSwatchIndex: Int,
    opacity: Float,
    onGridSelected: (Int) -> Unit,
    onSwatchSelected: (Int) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    initialColor: Color? = null,
    onColorChange: (Color) -> Unit = {},
    panelModifier: Modifier = Modifier,
) {
    val swatches = pickerSwatches(dark)
    val leadingToolbarGlyph = rememberSceneSubtreeText(file, "Leading Button")
    val trailingToolbarGlyph = rememberSceneSubtreeText(file, "Trailing Button")
    val chromeIconColor = colors.label.copy(alpha = 0.6f)
    val segmentedTrackColor = if (dark) Color(0xFF303033) else Color(0xFFDCDCDD)
    val segmentedSelectedColor = if (dark) Color(0xFF676769) else Color.White
    val addButtonColor = if (dark) Color(0xFF3C3C3F) else Color(0xFFD4D4D4)
    var pickerMode by rememberSaveable { mutableIntStateOf(0) }
    var editedColor by rememberSaveable(stateSaver = AndroidPickerColorSaver) { mutableStateOf(initialColor?.copy(alpha = 1f)) }
    val baseSelectedColor = if (selectedGridIndex == 6 * PickerGridColumns + 3 && selectedSwatchIndex == 6) {
        PickerDefaultColor
    } else if (selectedSwatchIndex in swatches.indices) {
        swatches[selectedSwatchIndex]
    } else {
        colorPickerGridColor(selectedGridIndex)
    }
    val selectedColor = editedColor ?: baseSelectedColor
    // Business output only; the framework's visual tree remains unchanged.
    val output = rememberUpdatedState(onColorChange)
    androidx.compose.runtime.LaunchedEffect(selectedColor, opacity) {
        output.value(selectedColor.copy(alpha = opacity))
    }
    Ios27BottomSheetDialog(
        backdrop = backdrop,
        dark = dark,
        height = 599.dp,
        scrimColor = Color.Transparent,
        onDismissRequest = onDismiss,
        panelModifier = panelModifier,
    ) {
            Column(
                Modifier.fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(49.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(24.dp).height(44.dp)
                            .semantics { contentDescription = "取色器" }
                            .ios27PressedClickable(
                                shape = RoundedCornerShape(12.dp),
                                onClick = { },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        leadingToolbarGlyph?.let { glyph ->
                            SceneGlyphText(
                                text = glyph,
                                tint = if (dark) chromeIconColor else glyph.color ?: chromeIconColor,
                                modifier = Modifier.requiredSize(36.dp, 44.dp),
                            )
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("颜色", style = PickerTitleStyle, color = colors.label)
                    }
                    Box(
                        Modifier.width(24.dp).height(44.dp)
                            .semantics { contentDescription = "关闭颜色选择器" }
                            .ios27PressedClickable(
                                shape = RoundedCornerShape(12.dp),
                                onClick = onDismiss,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        trailingToolbarGlyph?.let { glyph ->
                            SceneGlyphText(
                                text = glyph,
                                tint = if (dark) chromeIconColor else glyph.color ?: chromeIconColor,
                                modifier = Modifier.requiredSize(36.dp, 44.dp),
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.TopCenter) {
                    Row(
                        Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(16.dp))
                            .background(segmentedTrackColor)
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf("网格", "光谱", "滑块").forEachIndexed { index, label ->
                            Box(
                                Modifier.weight(1f).fillMaxSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (index == pickerMode) segmentedSelectedColor
                                        else Color.Transparent
                                    )
                                    .ios27PressedClickable(
                                        shape = RoundedCornerShape(14.dp),
                                        role = Role.Tab,
                                        onClick = { pickerMode = index },
                                    ).semantics { selected = index == pickerMode },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    style = PickerSegmentStyle.copy(
                                        fontWeight = if (index == pickerMode) FontWeight.Medium
                                        else FontWeight.Normal,
                                    ),
                                    color = colors.label,
                                )
                            }
                        }
                    }
                }
                when (pickerMode) {
                    0 -> PickerGrid(selectedGridIndex) {
                        editedColor = null
                        onGridSelected(it)
                    }
                    1 -> PickerSpectrum(selectedColor) {
                        editedColor = it
                    }
                    else -> PickerSliders(
                        color = selectedColor,
                        colors = colors,
                        dark = dark,
                        onColorChange = { editedColor = it },
                    )
                }
                Box(Modifier.fillMaxWidth().height(95.dp)) {
                    Text("不透明度", style = PickerOpacityStyle, color = colors.label,
                        modifier = Modifier.align(Alignment.TopStart).offset(y = 15.dp))
                    Row(Modifier.fillMaxWidth().height(34.dp).align(Alignment.TopStart).offset(y = 39.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        OpacityTrack(selectedColor, opacity, dark, modifier = Modifier.weight(1f), onOpacityChange)
                        Box(Modifier.width(75.dp).height(34.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (dark) Color.Black else colors.group), contentAlignment = Alignment.Center) {
                            Text("${(opacity * 100).toInt()}%", style = PickerValueStyle, color = colors.label)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(107.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp)
                        .background(if (dark) Color(0xFF2E2E30) else Color(0xFFD1D1D2)))
                    Box(Modifier.size(75.dp).align(Alignment.TopStart).offset(y = 20.dp)
                        .clip(RoundedCornerShape(10.dp)).background(selectedColor))
                    PickerPresetColors(
                        selectedColor = selectedColor.copy(alpha = opacity),
                        defaults = swatches,
                        addButtonColor = addButtonColor,
                        onSelected = { editedColor = it.copy(alpha = 1f); onOpacityChange(it.alpha) },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(start = 105.dp).offset(y = 20.dp),
                    )
                }
            }
        }
    }
