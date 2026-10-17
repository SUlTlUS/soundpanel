package dev.glass.soundbar

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.zeaze.tianyinwallpaper.backdrop.backdrops.rememberLayerBackdrop
import com.zeaze.tianyinwallpaper.backdrop.backdrops.layerBackdrop
import com.zeaze.tianyinwallpaper.backdrop.Backdrop
import com.zeaze.tianyinwallpaper.ui.commom.ProgressiveBlurContent
import dev.liquidglass.ios27.Ios27Tokens
import dev.liquidglass.ios27.Ios27AndroidDesignBehavior
import dev.liquidglass.ios27.LocalIos27PageColors

/**
 * Theme-style page chrome: the large title scrolls with content below a 54dp gap.
 * The progressive top blur follows the current page's scroll position.
 * Recorder and chrome remain independent sibling layers.
 */
@Composable
internal fun SettingsPageScaffold(
    scrollState: ScrollState,
    dark: Boolean,
    onRestart: () -> Unit,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modalContent: @Composable (Backdrop) -> Unit,
    content: @Composable BoxScope.(topPadding: Dp) -> Unit,
) {
    val colors = LocalIos27PageColors.current
    val backdrop = rememberLayerBackdrop { drawRect(colors.page); drawContent() }
    val systemDensity = LocalDensity.current
    val topInsetPx = with(systemDensity) {
        WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().toPx()
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.page)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        // Compute from system dp before entering the scaled page density.
        val native = LocalSettingsAppearance.current.native != null
        val scale = if (native) 1f else (minOf(maxWidth.value, maxHeight.value) / 402f).coerceIn(0.85f, 1f)
        val pageDensity = Density(systemDensity.density * scale, systemDensity.fontScale)
        Ios27AndroidDesignBehavior(pageDensity) {
            val topInset = with(pageDensity) { topInsetPx.toDp() }
            Box(Modifier.fillMaxSize()) {
                if (native) Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(colors.page))
                val source = if (native) Modifier else Modifier.layerBackdrop(backdrop)
                Box(Modifier.fillMaxSize().background(colors.page).then(source)) {
                    content(topInset + Ios27Tokens.CompactHeaderHeight.dp)
                }
                // Sibling overlays stay outside the recorder to avoid sampling themselves.
                ProgressiveBlurContent(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    backdrop = backdrop,
                    isLightTheme = !dark,
                    strength = 1f,
                    visibility = (scrollState.value / with(pageDensity) { 30.dp.toPx() }).coerceIn(0f, 1f),
                    surfaceColor = colors.page,
                    // The title scrolls with content; only the safe inset is fixed.
                    appBarHeight = topInset,
                    scrollDriven = true,
                )
                SettingsTabBar(
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = onTabSelected,
                    onRestart = onRestart,
                    backdrop = backdrop,
                    isLightTheme = !dark,
                    // COUI's 88dp host already centers the 56dp bar with 16dp below it.
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (native) 0.dp else 16.dp),
                )
                modalContent(backdrop)
            }
        }
    }
}

internal val RestartIcon = ImageVector.Builder(
    name = "RestartScope", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black)) {
        moveTo(17.65f, 6.35f)
        curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
        curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
        curveTo(4f, 16.42f, 7.58f, 20f, 12f, 20f)
        curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
        horizontalLineTo(17.65f)
        curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
        curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
        curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
        curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
        lineTo(13f, 11f)
        horizontalLineTo(20f)
        verticalLineTo(4f)
        close()
    }
}.build()
