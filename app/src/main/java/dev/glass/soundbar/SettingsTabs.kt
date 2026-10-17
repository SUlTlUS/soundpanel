package dev.glass.soundbar

import android.view.animation.OvershootInterpolator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeaze.tianyinwallpaper.backdrop.Backdrop
import com.zeaze.tianyinwallpaper.catalog.components.LiquidBottomTab
import com.zeaze.tianyinwallpaper.catalog.components.LiquidBottomTabsRegular
import com.zeaze.tianyinwallpaper.catalog.components.LiquidBottomTabsStyle
import com.zeaze.tianyinwallpaper.catalog.components.LiquidButton
import com.zeaze.tianyinwallpaper.catalog.components.LiquidButtonStyle
import dev.liquidglass.ios27.LocalIos27PageColors
import kotlin.math.roundToInt

internal enum class SettingsTab(val label: String, val title: String, val icon: Int) {
    PANEL("面板", "音量面板", R.drawable.ic_tab_panel),
    SHORTCUTS("快捷", "快捷按钮", R.drawable.ic_tab_shortcuts),
    SETTINGS("设置", "设置", R.drawable.ic_tab_settings),
}

/** Framework splitSearch geometry; the accessory shares the track's glass material. */
@Composable
internal fun SettingsTabBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    isLightTheme: Boolean,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalSettingsAppearance.current.native != null) {
        NativeSettingsTabBar(selectedTabIndex, onTabSelected, onRestart, modifier)
        return
    }
    val colors = LocalIos27PageColors.current
    val baseColor = if (isLightTheme) Color(0xFFFAFAFA) else Color(0xFF121212)
    val style = LiquidBottomTabsStyle.default(isLightTheme).copy(
        accentColor = colors.accent,
        containerColor = baseColor.copy(alpha = 0.63f),
        fallbackContainerColor = baseColor.copy(alpha = 0.90f),
        trackHeight = 62.dp, indicatorHeight = 54.dp,
        trackBlurRadius = 2.dp,
        indicatorLensWidth = 7.dp,
        indicatorLensHeight = 9.8.dp,
        indicatorInnerShadowRadius = 8.dp,
        hiddenContentPressedScale = 1.2f,
    )
    Row(
        modifier = modifier.height(style.trackHeight),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        LiquidBottomTabsRegular(
            selectedTabIndex = { selectedTabIndex },
            onTabSelected = onTabSelected,
            backdrop = backdrop,
            tabsCount = SettingsTab.entries.size,
            isLightTheme = isLightTheme,
            itemWidth = 94.dp,
            itemSpacing = (-8).dp,
            style = style,
        ) {
            SettingsTab.entries.forEachIndexed { index, tab ->
                LiquidBottomTab(
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.semantics { selected = selectedTabIndex == index },
                ) {
                    val tint = if (selectedTabIndex == index) colors.accent else colors.secondaryLabel
                    Icon(painterResource(tab.icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(tab.label, color = tint, style = MaterialTheme.typography.body1)
                }
            }
        }
        LiquidButton(
            onClick = onRestart,
            backdrop = backdrop,
            surfaceColor = style.containerColor,
            style = LiquidButtonStyle.Default.copy(
                height = style.trackHeight,
                blurRadius = style.trackBlurRadius,
                lensRadiusX = style.trackLensRadius,
                lensRadiusY = style.trackLensRadius,
            ),
            modifier = Modifier.size(62.dp).semantics { contentDescription = "重启作用域" },
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(RestartIcon, contentDescription = null, tint = colors.label, modifier = Modifier.size(24.dp))
        }
    }
}

private val tabSettleInterpolator = OvershootInterpolator(1.1f)
private val tabSettleEasing = Easing { tabSettleInterpolator.getInterpolation(it) }

/** Framework QweaTabPageTransition motion, shared with the Theme host. */
@Composable
internal fun SettingsTabPageTransition(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    AnimatedContent(
        targetState = selectedTabIndex,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            val direction = if (targetState >= initialState) 1 else -1
            val enter = slideInHorizontally(tween(380, easing = tabSettleEasing)) {
                (direction * it * 0.12f).roundToInt()
            } + scaleIn(tween(380, easing = tabSettleEasing), initialScale = 0.985f) +
                fadeIn(tween(180))
            val exit = slideOutHorizontally(tween(380, easing = tabSettleEasing)) {
                (-direction * it * 0.06f).roundToInt()
            } + scaleOut(tween(380, easing = tabSettleEasing), targetScale = 0.992f) +
                fadeOut(tween(160))
            enter togetherWith exit
        },
        label = "SettingsTabPageTransition",
    ) { page ->
        Box(Modifier.fillMaxSize()) { content(page) }
    }
}
