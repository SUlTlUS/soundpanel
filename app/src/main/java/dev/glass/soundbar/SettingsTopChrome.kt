package dev.glass.soundbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeaze.tianyinwallpaper.backdrop.backdrops.rememberLayerBackdrop
import com.zeaze.tianyinwallpaper.backdrop.backdrops.layerBackdrop
import com.zeaze.tianyinwallpaper.catalog.components.LiquidButton
import com.zeaze.tianyinwallpaper.ui.commom.ProgressiveBlurContent
import dev.liquidglass.ios27.Ios27Tokens
import dev.liquidglass.ios27.LocalIos27PageColors

/**
 * Business-content adapter for the framework's Compact Large Title (12740-24021).
 * Keeps its 54dp toolbar, 34/41sp title and 44dp trailing glass control; only the
 * source title and glyph are replaced. Recorder/chrome layering follows
 * ExampleRecordedResponsivePage + ExampleTopChrome + ExampleNavBar.
 */
@Composable
internal fun SettingsPageScaffold(
    title: String,
    dark: Boolean,
    onRestart: () -> Unit,
    content: @Composable BoxScope.(topPadding: Dp) -> Unit,
) {
    val colors = LocalIos27PageColors.current
    val backdrop = rememberLayerBackdrop { drawRect(colors.page); drawContent() }
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    Box(
        Modifier.fillMaxSize().background(colors.page)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        Box(Modifier.fillMaxSize().background(colors.page).layerBackdrop(backdrop)) {
            content(topInset + Ios27Tokens.CompactHeaderHeight.dp)
        }
        // Sibling overlays stay outside the recorder to avoid sampling themselves.
        ProgressiveBlurContent(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            backdrop = backdrop,
            isLightTheme = !dark,
        )
        Box(
            Modifier.align(Alignment.TopCenter).widthIn(max = Ios27Tokens.ReadableWidth.dp)
                .fillMaxWidth().padding(top = topInset)
                .height(Ios27Tokens.CompactHeaderHeight.dp)
                .padding(horizontal = Ios27Tokens.ContentMargin.dp),
        ) {
            Text(
                title,
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .padding(end = 52.dp).semantics { heading() },
                color = colors.label,
                style = Ios27Tokens.Type.LargeTitle.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LiquidButton(
                onClick = onRestart,
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.TopEnd).size(44.dp),
                buttonHeight = 44.dp,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(RestartIcon, contentDescription = "重启作用域", tint = colors.label, modifier = Modifier.size(22.dp))
            }
        }
    }
}

private val RestartIcon = ImageVector.Builder(
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
