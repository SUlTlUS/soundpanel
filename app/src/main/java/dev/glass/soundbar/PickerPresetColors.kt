package dev.glass.soundbar

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.liquidglass.ios27.LocalIos27PageColors
import dev.liquidglass.ios27.ios27ListPressMask
import dev.liquidglass.ios27.ios27PressedClickable

/** The original two-row, five-column palette footprint, with persistent editable pages. */
@Composable
internal fun PickerPresetColors(
    selectedColor: Color,
    defaults: List<Color>,
    addButtonColor: Color,
    onSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("color_picker", Context.MODE_PRIVATE) }
    var entries by remember(preferences) {
        mutableStateOf(PickerColorPresets.decode(preferences.getString("presets", null)))
    }
    val defaultArgb = defaults.map { it.toArgb() }
    val currentArgb = selectedColor.toArgb()
    val pageCount = entries.size / 10 + 1
    val pager = rememberPagerState(pageCount = { pageCount })
    var requestedPage by remember { mutableIntStateOf(-1) }
    val colors = LocalIos27PageColors.current
    fun save(value: List<PickerColorPreset>) {
        preferences.edit().putString("presets", PickerColorPresets.encode(value)).apply()
        entries = value
    }
    LaunchedEffect(pageCount, requestedPage) {
        if (requestedPage >= 0) {
            pager.animateScrollToPage(requestedPage.coerceIn(0, pageCount - 1))
            requestedPage = -1
        } else if (pager.currentPage >= pageCount) pager.scrollToPage(pageCount - 1)
    }
    val addColor = {
        val next = PickerColorPresets.add(entries, currentArgb, defaultArgb)
        val added = next.size > entries.size
        val index = next.indexOfFirst { PickerColorPresets.color(it, defaultArgb) == currentArgb }
        if (next != entries) save(next)
        requestedPage = index / 10
        Toast.makeText(context, if (added) "已添加颜色，左右滑动翻页，长按删除" else "颜色已在预设中，长按色点可删除", Toast.LENGTH_SHORT).show()
    }
    Column(modifier) {
    HorizontalPager(
        state = pager,
        pageSpacing = 15.dp,
        modifier = Modifier.height(75.dp).semantics {
            contentDescription = "颜色预设，第 ${pager.currentPage + 1} 页，共 $pageCount 页；左右滑动翻页，长按色点删除"
        },
    ) { page ->
        val colorsOnPage = (entries.size - page * 10).coerceIn(0, 10)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            repeat(2) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(5) { column ->
                        if (page == pageCount - 1 && row * 5 + column == colorsOnPage) {
                            Box(
                                Modifier.size(30.dp).background(addButtonColor, CircleShape)
                                    .ios27PressedClickable(shape = CircleShape, role = Role.Button, onClick = addColor)
                                    .semantics { contentDescription = "添加当前颜色到预设" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(Modifier.size(14.666.dp)) {
                                    val stroke = 2.667.dp.toPx()
                                    drawLine(colors.label, Offset(size.width / 2f, stroke / 2f),
                                        Offset(size.width / 2f, size.height - stroke / 2f), strokeWidth = stroke, cap = StrokeCap.Round)
                                    drawLine(colors.label, Offset(stroke / 2f, size.height / 2f),
                                        Offset(size.width - stroke / 2f, size.height / 2f), strokeWidth = stroke, cap = StrokeCap.Round)
                                }
                            }
                        } else {
                            val index = page * 10 + row * 5 + column
                            val entry = entries.getOrNull(index)
                            if (entry == null) Spacer(Modifier.size(30.dp)) else key(entry) {
                                val argb = PickerColorPresets.color(entry, defaultArgb)
                                val color = Color(argb)
                                val interactions = remember { MutableInteractionSource() }
                                val pressed by interactions.collectIsPressedAsState()
                                val remove = {
                                    save(entries.filterNot { it == entry })
                                    Toast.makeText(context, "已删除预设颜色", Toast.LENGTH_SHORT).show()
                                }
                                Box(
                                    Modifier.size(30.dp).background(color, CircleShape).clip(CircleShape)
                                        .combinedClickable(interactionSource = interactions, indication = null,
                                            role = Role.RadioButton, onLongClickLabel = "删除预设颜色",
                                            onLongClick = remove, onClick = { onSelected(color) })
                                        .ios27ListPressMask(pressed, CircleShape)
                                        .semantics {
                                            contentDescription = "预设颜色 ${index + 1}，#%08X".format(argb)
                                            selected = currentArgb == argb
                                            customActions = listOf(CustomAccessibilityAction("删除预设颜色") { remove(); true })
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (currentArgb == argb) Box(Modifier.size(22.dp).border(2.dp, Color.White, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "第 ${pager.currentPage + 1} 页，共 $pageCount 页"
            },
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { page ->
            Box(Modifier.size(4.dp).background(
                colors.label.copy(alpha = if (pager.currentPage == page) 0.8f else 0.2f), CircleShape,
            ))
        }
    }
    }
}
