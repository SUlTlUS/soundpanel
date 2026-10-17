package dev.glass.soundbar

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.liquidglass.coloros17.ColorOs17NavigationItem
import dev.liquidglass.coloros17.ColorOs17SettingsComponents
import dev.liquidglass.ios27.Ios27ListRow
import dev.liquidglass.ios27.Ios27PageTheme
import dev.liquidglass.ios27.Ios27Tokens
import dev.liquidglass.ios27.LocalIos27PageColors
import kotlin.math.roundToInt

internal data class SettingsAppearanceState(
    val liquidGlass: Boolean,
    val native: ColorOs17SettingsComponents?,
    val onChange: (Boolean) -> Unit,
)

internal val LocalSettingsAppearance = staticCompositionLocalOf<SettingsAppearanceState> {
    error("SettingsAppearanceHost is required")
}

@Composable
internal fun SettingsAppearanceHost(activity: MainActivity, content: @Composable () -> Unit) {
    val pageState = rememberSaveableStateHolder()
    var glass by remember { mutableStateOf(AppAppearance.liquidGlass(activity)) }
    val dark = isSystemInDarkTheme()
    val configuration = Configuration(LocalConfiguration.current)
    var result by remember(activity, glass, configuration) {
        mutableStateOf<Result<ColorOs17SettingsComponents>?>(null)
    }
    LaunchedEffect(activity, glass, configuration) {
        if (!glass) result = ColorOs17SettingsComponents.open(activity, dark)
    }
    val session = result?.getOrNull()
    DisposableEffect(session) { onDispose { session?.close() } }
    val change: (Boolean) -> Unit = {
        AppAppearance.setLiquidGlass(activity, it)
        glass = it
    }
    CompositionLocalProvider(LocalSettingsAppearance provides SettingsAppearanceState(glass, session, change)) {
        if (glass || session != null) pageState.SaveableStateProvider("settings") { content() }
        else Ios27PageTheme(darkTheme = dark) {
            val colors = LocalIos27PageColors.current
            Column(Modifier.fillMaxSize().background(colors.page).safeDrawingPadding().padding(16.dp)) {
                Text(if (result == null) "正在加载 ColorOS 17 原组件…" else
                    "无法调用 ColorOS 17 原组件：\n${ColorOs17SettingsComponents.failure(result!!.exceptionOrNull()!!)}",
                    color = colors.label)
                Ios27ListRow("使用液态玻璃", onClick = { change(true) })
            }
        }
    }
}

/** The container only hosts foreign Views or an explicit error; it never draws replacement controls. */
@Composable
private fun NativeSettingsControl(
    session: ColorOs17SettingsComponents,
    modifier: Modifier = Modifier,
    create: () -> Result<View>,
    update: (View) -> Result<Unit> = { Result.success(Unit) },
) {
    key(session) {
        AndroidView(
            modifier = modifier,
            factory = { host ->
                FrameLayout(host).apply {
                    clipChildren = false
                    clipToPadding = false
                    create().onSuccess { view ->
                        tag = view
                        addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER))
                    }.onFailure { showNativeFailure(it) }
                }
            },
            update = { root ->
                (root.tag as? View)?.let { view -> update(view).onFailure { root.showNativeFailure(it) } }
            },
            onRelease = { it.removeAllViews() },
        )
    }
}

private fun FrameLayout.showNativeFailure(error: Throwable) {
    tag = null
    removeAllViews()
    addView(TextView(context).apply {
        text = "原组件不可用：${ColorOs17SettingsComponents.failure(error)}"
    })
}

@Composable
internal fun NativeSettingsSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val session = LocalSettingsAppearance.current.native!!
    val callback = rememberUpdatedState(onChange)
    NativeSettingsControl(session, Modifier.wrapContentSize(), create = {
        session.createSwitch(checked) { callback.value(it) }.onSuccess { it.contentDescription = title }
    }, update = { view ->
        // Programmatic state synchronization must not save settings or trigger a second toggle.
        (view as CompoundButton).apply {
            setOnCheckedChangeListener(null)
            isChecked = checked
            setOnCheckedChangeListener { _, value -> callback.value(value) }
        }
        Result.success(Unit)
    })
}

@Composable
internal fun NativeSettingsSlider(title: String, value: Float, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    val session = LocalSettingsAppearance.current.native!!
    val change = rememberUpdatedState(onChange)
    val finished = rememberUpdatedState(onFinished)
    val progress = (value * 1000).roundToInt().coerceIn(0, 1000)
    NativeSettingsControl(session, Modifier.fillMaxWidth()
        .padding(horizontal = Ios27Tokens.RowPadding.dp).heightIn(min = 52.dp), create = {
        session.createSlider(1000, progress, { change.value(it / 1000f) }, { finished.value() })
            .onSuccess { it.contentDescription = title }
    }, update = { session.updateSlider(it, progress) })
}

@Composable
internal fun NativeSettingsTabBar(selected: Int, onSelected: (Int) -> Unit, onRestart: () -> Unit, modifier: Modifier) {
    val session = LocalSettingsAppearance.current.native!!
    val host = LocalContext.current
    val select = rememberUpdatedState(onSelected)
    val restart = rememberUpdatedState(onRestart)
    // Navigation already contributes 16dp on each side; the circular action has none.
    Row(modifier.widthIn(max = Ios27Tokens.ReadableWidth.dp).fillMaxWidth()
        .padding(end = Ios27Tokens.RowPadding.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterHorizontally),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        NativeSettingsControl(session, Modifier.weight(1f, fill = false).wrapContentHeight(), create = {
            session.createNavigation(SettingsTab.entries.mapIndexed { index, tab ->
                ColorOs17NavigationItem(index + 1, tab.label, host.getDrawable(tab.icon))
            }, selected + 1) { select.value(it - 1) }
        }, update = { view ->
            session.updateNavigation(view, selected + 1).map {
                tintNativeNavigationIcons(view)
            }
        })
        NativeSettingsControl(session, Modifier.size(62.dp), create = {
            session.createFloatingActionButton("重启作用域", host.getDrawable(R.drawable.ic_restart_scope)!!) { restart.value() }
        })
    }
}

/** COUI updates title state colors but leaves caller-supplied Drawable colors unchanged. */
private fun tintNativeNavigationIcons(navigation: View) {
    fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
    }
    SettingsTab.entries.forEachIndexed { index, tab ->
        val item = navigation.findViewById<View>(index + 1) ?: return@forEachIndexed
        val views = descendants(item).toList()
        val title = views.filterIsInstance<TextView>().firstOrNull { it.text.toString() == tab.label }
            ?: return@forEachIndexed
        val icon = views.filterIsInstance<ImageView>().firstOrNull() ?: return@forEachIndexed
        icon.imageTintList = title.textColors
    }
}

@Composable
internal fun NativeHookLogDialog(visible: Boolean, priority: Int, onDismiss: () -> Unit, onSelected: (Int) -> Unit) {
    val session = LocalSettingsAppearance.current.native!!
    val dismiss = rememberUpdatedState(onDismiss)
    val select = rememberUpdatedState(onSelected)
    var failure by remember(visible, session) { mutableStateOf<String?>(null) }
    DisposableEffect(visible, session) {
        val dialog = if (visible) session.showSingleChoiceDialog(
            "Hook 日志级别", HookLogLevel.entries.map { it.title },
            HookLogLevel.entries.indexOf(HookLogLevel.fromPriority(priority)),
            { select.value(HookLogLevel.entries[it].priority) }, { dismiss.value() },
        ).onFailure { failure = ColorOs17SettingsComponents.failure(it) }.getOrNull() else null
        dialog?.window?.apply {
            setDimAmount(0f)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        onDispose { dialog?.dismiss() }
    }
    failure?.let { message ->
        val colors = LocalIos27PageColors.current
        Column(Modifier.fillMaxWidth().background(colors.group).padding(16.dp)) {
            Text("原日志弹窗不可用：$message", color = colors.label)
            Ios27ListRow("关闭", onClick = onDismiss)
        }
    }
}
