package dev.glass.soundbar

import android.content.Context
import android.os.Bundle
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.view.WindowInsetsController
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.liquidglass.ios27.Ios27ListRow
import dev.liquidglass.ios27.Ios27ListTypography
import dev.liquidglass.ios27.LocalIos27ListTypography
import dev.liquidglass.ios27.Ios27ListSection
import dev.liquidglass.ios27.Ios27ListRows
import dev.liquidglass.ios27.Ios27Tokens
import dev.liquidglass.ios27.Ios27PageContent
import dev.liquidglass.ios27.Ios27PageHeader
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import com.zeaze.tianyinwallpaper.backdrop.backdrops.rememberCanvasBackdrop
import com.zeaze.tianyinwallpaper.catalog.components.LiquidToggle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsAppearanceHost(this) {
                CompositionLocalProvider(LocalIos27ListTypography provides SettingsListTypography) {
                    ActivationPage(this)
                }
            }
        }
    }
}

// ColorOS Settings: COUI preference titles/categories use sans-serif-medium.
private val SettingsListTypography = Ios27ListTypography(
    title = Ios27Tokens.Type.Body.copy(fontWeight = FontWeight.Medium),
    sectionWeight = FontWeight.Medium,
)

@Composable
private fun ActivationPage(activity: MainActivity) {
    val context: Context = activity
    val dark = isSystemInDarkTheme()
    var active by remember { mutableStateOf(ActivationStatus.isReady()) }
    var settings by remember { mutableStateOf(PanelSettings.local(context)) }
    var launcherVisible by remember { mutableStateOf(LauncherIcon.isVisible(context)) }
    var logInspectorVisible by rememberSaveable { mutableStateOf(false) }
    var iconColorStream by rememberSaveable { mutableIntStateOf(0) }
    var iconColorVisible by rememberSaveable { mutableStateOf(false) }
    var iconColorRequest by rememberSaveable { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val panelScroll = rememberScrollState()
    val shortcutsScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()
    val selectTab = rememberUpdatedState<(Int) -> Unit> { index ->
        if (index != tab) {
            tab = index
            activity.window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    val onTabSelected = remember { { index: Int -> selectTab.value(index) } }
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
                active = ActivationStatus.isReady()
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
            scrollState = when (tab) {
                0 -> panelScroll
                1 -> shortcutsScroll
                else -> settingsScroll
            },
            dark = dark,
            onRestart = { ActivationStatus.requestScopeRestart() },
            selectedTabIndex = tab,
            onTabSelected = onTabSelected,
            modalContent = { backdrop ->
                androidx.compose.runtime.key(iconColorRequest) {
                    val stream = VolumeIconStream.entries[iconColorStream]
                    VolumeIconColorPicker(
                        visible = iconColorVisible, stream = stream, initialColor = stream.color(settings),
                        dark = dark, backdrop = backdrop,
                        onSave = { saveSettings(stream.withColor(settings, it)) },
                        onDismiss = { iconColorVisible = false },
                    )
                }
                HookLogInspector(
                    visible = logInspectorVisible,
                    priority = settings.hookLogLevel,
                    dark = dark,
                    backdrop = backdrop,
                    onDismiss = { logInspectorVisible = false },
                    onLevelSelected = { saveSettings(settings.copy(hookLogLevel = it)) },
                )
            },
        ) { topPadding ->
            SettingsTabPageTransition(tab, Modifier.fillMaxSize()) { page ->
                Ios27PageContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(when (page) {
                            0 -> panelScroll
                            1 -> shortcutsScroll
                            else -> settingsScroll
                        })
                        .padding(top = topPadding, bottom = 108.dp),
                ) {
                    Ios27PageHeader(SettingsTab.entries[page].title, compact = false)
                    if (page == 0) {
                        AppearanceSection(settings, ::saveSettings)
                        VolumeIconColorSection(settings, onReset = {
                            saveSettings(settings.copy(mediaIconColor = null, ringerIconColor = null,
                                notificationIconColor = null, alarmIconColor = null))
                        }) { stream ->
                            iconColorStream = stream.ordinal
                            iconColorRequest++
                            iconColorVisible = true
                        }
                    } else if (page == 1) {
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
                        Ios27ListSection(title = "耳机模式切换") {
                            val colors = LocalIos27PageColors.current
                            SettingsListRows(EarphoneMode.entries) { mode ->
                                val checked = settings.headsetModes and mode.bit != 0
                                Ios27ListRow(
                                    title = mode.title,
                                    modifier = Modifier.semantics { selected = checked },
                                    disclosure = false,
                                    onClick = { saveSettings(settings.copy(headsetModes = settings.headsetModes xor mode.bit)) },
                                    trailing = {
                                        if (checked) Icon(Icons.Rounded.Check, "已选择", tint = colors.accent, modifier = Modifier.size(22.dp))
                                    },
                                )
                            }
                        }
                    } else {
                        Ios27ListSection(title = "模块") {
                            SettingsListRows(MaintenanceRow.entries) { row ->
                                when (row) {
                                    MaintenanceRow.ENABLED -> SettingSwitchRow("启用模块", settings.enabled) {
                                        saveSettings(settings.copy(enabled = it))
                                    }
                                    MaintenanceRow.STATUS -> Ios27ListRow(
                                        title = "LSPosed",
                                        detail = if (active) "已就绪" else "未激活",
                                        onClick = if (active) null else ({
                                            ActivationStatus.requestScope(context) { error ->
                                                activity.runOnUiThread {
                                                    active = ActivationStatus.isReady()
                                                    if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }),
                                        disclosure = !active,
                                    )
                                    MaintenanceRow.ICON -> SettingSwitchRow("显示桌面图标", launcherVisible) {
                                        LauncherIcon.setVisible(context, it)
                                        launcherVisible = LauncherIcon.isVisible(context)
                                    }
                                }
                            }
                        }
                        Ios27ListSection(title = "界面") {
                            val appearance = LocalSettingsAppearance.current
                            SettingSwitchRow("液态玻璃", appearance.liquidGlass, appearance.onChange)
                        }
                        Ios27ListSection(title = "日志") {
                            SettingsListRows(listOf(false, true)) { export ->
                                if (export) Ios27ListRow(
                                    title = "导出日志",
                                    onClick = { saveLogLauncher.launch("soundbar-debug.log") },
                                ) else Ios27ListRow(
                                    title = "Hook 日志级别",
                                    detail = HookLogLevel.fromPriority(settings.hookLogLevel).title,
                                    onClick = { logInspectorVisible = true },
                                )
                            }
                        }
                        Ios27ListSection(title = "关于") {
                            val colors = LocalIos27PageColors.current
                            SettingsListRows(listOf(
                                "模块版本" to BuildConfig.VERSION_NAME,
                                "系统" to "Android ${Build.VERSION.RELEASE}",
                                "系统版本" to Build.DISPLAY.removePrefix("${Build.MODEL}_"),
                                "设备型号" to "${Build.MANUFACTURER} ${Build.MODEL}",
                            )) { (title, value) ->
                                Ios27ListRow(title = title, trailing = {
                                    Text(value, color = colors.secondaryLabel, style = Ios27Tokens.Type.Body,
                                        textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 200.dp))
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class MaintenanceRow { STATUS, ENABLED, ICON }
private data class SwitchSetting(val title: String, val checked: Boolean, val onCheckedChange: (Boolean) -> Unit)

@Composable
private fun AppearanceSection(settings: PanelSettings, onChange: (PanelSettings) -> Unit) {
    val context = LocalContext.current
    var gameScopeReady by remember { mutableStateOf(ActivationStatus.hasGameScope()) }
    LaunchedEffect(settings.hideMultiAppVolume) {
        while (settings.hideMultiAppVolume) {
            gameScopeReady = ActivationStatus.hasGameScope()
            delay(2_000L)
        }
    }
    // Dragging only recomposes this group. Persist once on release instead of
    // blocking every drag frame with preferences, LSPosed IPC and notifyChange.
    var tone by remember(settings.tone) { mutableStateOf(settings.tone) }
    var darkTone by remember(settings.darkTone) { mutableStateOf(settings.darkTone) }
    var radiusScale by remember(settings.radiusScale) { mutableStateOf(settings.radiusScale) }
    var lengthScale by remember(settings.lengthScale) { mutableStateOf(settings.lengthScale) }
    var portraitPosition by remember(settings.portraitPosition) { mutableStateOf(settings.portraitPosition) }
    var landscapePosition by remember(settings.landscapePosition) { mutableStateOf(settings.landscapePosition) }
    Ios27ListSection(title = "底色") {
        SettingsListRows(listOf(false, true)) { dark ->
            if (dark) {
                SettingsSlider(
                    title = "深色模式底色", value = (darkTone + 1f) / 2f,
                    onValueChange = { darkTone = it * 2f - 1f },
                    onValueChangeFinished = { onChange(settings.copy(darkTone = darkTone)) },
                    onReset = { darkTone = 0f; onChange(settings.copy(darkTone = 0f)) },
                )
            } else {
                SettingsSlider(
                    title = "浅色模式底色", value = (tone + 1f) / 2f,
                    onValueChange = { tone = it * 2f - 1f },
                    onValueChangeFinished = { onChange(settings.copy(tone = tone)) },
                    onReset = { tone = 0f; onChange(settings.copy(tone = 0f)) },
                )
            }
        }
    }
    Ios27ListSection(title = "圆角") {
        SettingsSlider(
            title = "面板圆角", value = radiusScale, defaultValue = 1f,
            onValueChange = { radiusScale = it },
            onValueChangeFinished = { onChange(settings.copy(radiusScale = radiusScale)) },
            onReset = { radiusScale = 1f; onChange(settings.copy(radiusScale = 1f)) },
        )
    }
    Ios27ListSection(title = "长度") {
        SettingsSlider(
            title = "音量条长度", value = ((lengthScale - 1f) / 0.4f + 0.5f).coerceIn(0f, 1f),
            onValueChange = { lengthScale = 1f + (it - 0.5f) * 0.4f },
            onValueChangeFinished = { onChange(settings.copy(lengthScale = lengthScale)) },
            onReset = { lengthScale = 1f; onChange(settings.copy(lengthScale = 1f)) },
        )
    }
    Ios27ListSection(title = "垂直位置") {
        SettingsListRows(listOf(false, true)) { landscape ->
            if (landscape) {
                SettingsSlider(
                    title = "横屏垂直位置", value = (landscapePosition + 1f) / 2f,
                    onValueChange = { landscapePosition = it * 2f - 1f },
                    onValueChangeFinished = { onChange(settings.copy(landscapePosition = landscapePosition)) },
                    onReset = { landscapePosition = 0f; onChange(settings.copy(landscapePosition = 0f)) },
                )
            } else {
                SettingsSlider(
                    title = "竖屏垂直位置", value = (portraitPosition + 1f) / 2f,
                    onValueChange = { portraitPosition = it * 2f - 1f },
                    onValueChangeFinished = { onChange(settings.copy(portraitPosition = portraitPosition)) },
                    onReset = { portraitPosition = 0f; onChange(settings.copy(portraitPosition = 0f)) },
                )
            }
        }
    }
    Ios27ListSection(title = "显示") {
        val switches = listOf(
            SwitchSetting("流光轮廓", settings.borderHighlight) { onChange(settings.copy(borderHighlight = it)) },
            SwitchSetting("禁用音量条粗细效果", !settings.autoThin) { onChange(settings.copy(autoThin = !it)) },
            SwitchSetting("隐藏音量名称", settings.hideLabels) { onChange(settings.copy(hideLabels = it)) },
            SwitchSetting("关闭游戏时的自动多应用音量条", settings.hideMultiAppVolume) { onChange(settings.copy(hideMultiAppVolume = it)) },
            SwitchSetting("音量条显示在左侧", settings.leftSide) { onChange(settings.copy(leftSide = it)) },
            SwitchSetting("禁止应用覆盖系统音量条", settings.blockAppVolume) {
                onChange(settings.copy(blockAppVolume = it))
                if (it) Toast.makeText(context, "首次使用请在 LSPosed 勾选系统框架（system）并重启手机", Toast.LENGTH_LONG).show()
            },
        )
        SettingsListRows(switches) { row ->
            SettingSwitchRow(row.title, row.checked, row.onCheckedChange)
        }
    }
    if (settings.hideMultiAppVolume && !gameScopeReady) {
        Ios27ListSection(title = "应用音量条") {
            Ios27ListRow(
                title = "授权游戏助手作用域",
                detail = "首次授权后，隐藏开关才能对游戏中的应用音量条生效",
                onClick = {
                    ActivationStatus.requestGameScope(context) { error ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            gameScopeReady = ActivationStatus.hasGameScope()
                            if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
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
        if (LocalSettingsAppearance.current.native != null) {
            NativeSettingsSlider(title, value, onValueChange, onValueChangeFinished)
        } else {
            Ios27SliderRow(
                label = title, value = value, showLabel = false,
                onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished,
            )
        }
    }
}

@Composable
internal fun <T> SettingsListRows(
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
    if (LocalSettingsAppearance.current.native != null) {
        Ios27ListRow(title, onClick = { onCheckedChange(!checked) }, disclosure = false,
            trailing = { NativeSettingsSwitch(title, checked, onCheckedChange) })
        return
    }
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
