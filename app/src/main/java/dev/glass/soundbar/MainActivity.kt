package dev.glass.soundbar

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.liquidglass.ios27.Ios27ListRow
import dev.liquidglass.ios27.Ios27ListSection
import dev.liquidglass.ios27.Ios27ListRows
import dev.liquidglass.ios27.Ios27Tokens
import dev.liquidglass.ios27.Ios27PageContent
import dev.liquidglass.ios27.Ios27PageSurface
import dev.liquidglass.ios27.Ios27PageTheme
import dev.liquidglass.ios27.LocalIos27PageColors
import dev.liquidglass.ios27.Ios27SliderRow
import dev.liquidglass.ios27.ios27PressedClickable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.zeaze.tianyinwallpaper.backdrop.backdrops.rememberCanvasBackdrop
import com.zeaze.tianyinwallpaper.catalog.components.LiquidToggle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ActivationPage(this) }
    }
}

@Composable
private fun ActivationPage(activity: MainActivity) {
    val context: Context = activity
    val dark = isSystemInDarkTheme()
    var active by remember { mutableStateOf(ActivationStatus.isEnabledForSystemUi()) }
    var settings by remember { mutableStateOf(PanelSettings.local(context)) }
    fun saveSettings(value: PanelSettings) {
        settings = value
        PanelSettings.save(context, value)
    }
    val saveLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { destination ->
        if (destination == null) return@rememberLauncherForActivityResult
        val result = runCatching {
            val source = PanelSettings.uri.buildUpon().path("debug-log").build()
            val input = context.contentResolver.openInputStream(source)
                ?: error("Unable to open diagnostic snapshot")
            input.use { stream ->
                val output = context.contentResolver.openOutputStream(destination, "w")
                    ?: error("Unable to open destination")
                output.use { stream.copyTo(it) }
            }
        }
        Toast.makeText(
            context,
            if (result.isSuccess) "日志已保存" else "日志保存失败",
            Toast.LENGTH_SHORT,
        ).show()
    }

    LaunchedEffect(Unit) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                active = ActivationStatus.isEnabledForSystemUi()
                delay(2_000L)
            }
        }
    }

    LaunchedEffect(activity, dark) {
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val lightBars = if (dark) 0 else {
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        }
        activity.window.insetsController?.setSystemBarsAppearance(
            lightBars,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
    }

    Ios27PageTheme(
        darkTheme = dark,
        surface = Ios27PageSurface.Grouped,
    ) {
        SettingsPageScaffold(
            title = "音量面板",
            dark = dark,
            onRestart = { ActivationStatus.requestScopeRestart() },
        ) { topPadding ->
            Ios27PageContent(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = topPadding, bottom = Ios27Tokens.ContentMargin.dp),
            ) {
                AppearanceSection(settings, ::saveSettings)
                Ios27ListSection(title = "快捷按钮") {
                    val switches = listOf(
                        SwitchSetting("响铃模式", settings.ringerButton) { saveSettings(settings.copy(ringerButton = it)) },
                        SwitchSetting("勿扰", settings.dndButton) { saveSettings(settings.copy(dndButton = it)) },
                        SwitchSetting("耳机模式", settings.headsetButton) { saveSettings(settings.copy(headsetButton = it)) },
                    )
                    SettingsListRows(switches) { row ->
                        SettingSwitchRow(row.title, row.checked, row.onCheckedChange)
                    }
                }
                Ios27ListSection(title = "模块") {
                    SettingsListRows(MaintenanceRow.entries) { row ->
                        when (row) {
                            MaintenanceRow.STATUS -> Ios27ListRow(
                                title = "LSPosed",
                                detail = when (active) {
                                    true -> "已启用"
                                    false -> "SystemUI 未选中"
                                    null -> "无法读取"
                                },
                                disclosure = false,
                            )
                            MaintenanceRow.LOG -> Ios27ListRow(
                                title = "导出日志",
                                onClick = { saveLogLauncher.launch("soundbar-debug.log") },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class AppearanceRow { LIGHT_TONE, DARK_TONE, CORNERS, LABELS }
private enum class MaintenanceRow { STATUS, LOG }
private data class SwitchSetting(val title: String, val checked: Boolean, val onCheckedChange: (Boolean) -> Unit)

@Composable
private fun AppearanceSection(settings: PanelSettings, onChange: (PanelSettings) -> Unit) {
    // Dragging only recomposes this group. Persist once on release instead of
    // blocking every drag frame with preferences, LSPosed IPC and notifyChange.
    var tone by remember(settings.tone) { mutableStateOf(settings.tone) }
    var darkTone by remember(settings.darkTone) { mutableStateOf(settings.darkTone) }
    var radiusScale by remember(settings.radiusScale) { mutableStateOf(settings.radiusScale) }
    Ios27ListSection(title = "外观") {
        SettingsListRows(AppearanceRow.entries) { row ->
            when (row) {
                AppearanceRow.LIGHT_TONE -> SettingsSlider(
                    title = "浅色模式底色", value = (tone + 1f) / 2f,
                    onValueChange = { tone = it * 2f - 1f },
                    onValueChangeFinished = { onChange(settings.copy(tone = tone)) },
                    onReset = { tone = 0f; onChange(settings.copy(tone = 0f)) },
                )
                AppearanceRow.DARK_TONE -> SettingsSlider(
                    title = "深色模式底色", value = (darkTone + 1f) / 2f,
                    onValueChange = { darkTone = it * 2f - 1f },
                    onValueChangeFinished = { onChange(settings.copy(darkTone = darkTone)) },
                    onReset = { darkTone = 0f; onChange(settings.copy(darkTone = 0f)) },
                )
                AppearanceRow.CORNERS -> SettingsSlider(
                    title = "面板圆角", value = radiusScale, defaultValue = 1f,
                    onValueChange = { radiusScale = it },
                    onValueChangeFinished = { onChange(settings.copy(radiusScale = radiusScale)) },
                    onReset = { radiusScale = 1f; onChange(settings.copy(radiusScale = 1f)) },
                )
                AppearanceRow.LABELS -> SettingSwitchRow("隐藏音量名称", settings.hideLabels) {
                    onChange(settings.copy(hideLabels = it))
                }
            }
        }
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    value: Float,
    defaultValue: Float = 0.5f,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = LocalIos27PageColors.current
    Column(verticalArrangement = Arrangement.spacedBy((-8).dp)) {
        Ios27ListRow(title = title, trailing = {
            Text(
                text = if (value == defaultValue) "默认" else "还原",
                style = Ios27Tokens.Type.Body,
                color = if (value == defaultValue) colors.secondaryLabel else colors.accent,
                modifier = Modifier.ios27PressedClickable(
                    shape = RectangleShape, enabled = value != defaultValue, onClick = onReset,
                ),
            )
        })
        Ios27SliderRow(
            label = title, value = value, showLabel = false,
            onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
        )
    }
}

@Composable
private fun <T> SettingsListRows(
    items: List<T>,
    showSeparator: (Int) -> Boolean = { true },
    row: @Composable (T) -> Unit,
) {
    val separator = LocalIos27PageColors.current.separator
    // Keep framework row geometry and first/last corner masks. Center the 2dp
    // divider on the shared row edge instead of painting it inside the next row.
    Ios27ListRows(items.withIndex().toList(), showSeparator = { false }) { (index, item) ->
        Box(Modifier.fillMaxWidth().drawWithContent {
            drawContent()
            if (index > 0 && showSeparator(index)) {
                val inset = Ios27Tokens.SeparatorInset.dp.toPx()
                val thickness = Ios27Tokens.SeparatorRenderHeight.dp.toPx()
                drawRect(
                    separator,
                    topLeft = Offset(inset, -thickness / 2f),
                    size = Size((size.width - 2f * inset).coerceAtLeast(0f), thickness),
                )
            }
        }) {
            row(item)
        }
    }
}

@Composable
private fun SettingSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalIos27PageColors.current
    val backdrop = rememberCanvasBackdrop { drawRect(colors.group) }
    val light = colors.page != Ios27Tokens.PageDark
    Ios27ListRow(title, trailing = {
        LiquidToggle(
            selected = { checked },
            onSelect = onCheckedChange,
            backdrop = backdrop,
            isLightTheme = light,
            modifier = Modifier.semantics {
                contentDescription = title
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                onClick { onCheckedChange(!checked); true }
            },
        )
    })
}
