package dev.glass.soundbar

import android.content.Context
import android.os.Bundle
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.liquidglass.ios27.Ios27ActionButton
import dev.liquidglass.ios27.Ios27ListRow
import dev.liquidglass.ios27.Ios27ListSection
import dev.liquidglass.ios27.Ios27PageContent
import dev.liquidglass.ios27.Ios27PageHeader
import dev.liquidglass.ios27.Ios27PageSurface
import dev.liquidglass.ios27.Ios27PageTheme
import dev.liquidglass.ios27.LocalIos27PageColors
import kotlinx.coroutines.delay

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
    var active by remember { mutableStateOf(ActivationStatus.isActive(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            active = ActivationStatus.isActive(context)
            delay(2_000L)
        }
    }

    SideEffect {
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
        val colors = LocalIos27PageColors.current
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.page),
        ) {
            Ios27PageContent(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Ios27PageHeader(title = "音量面板")
                Ios27ListSection {
                    Ios27ListRow(
                        title = "LSPosed",
                        detail = if (active) "已激活" else "未激活",
                        disclosure = false,
                    )
                }
            }

            Ios27ActionButton(
                label = "重启作用域",
                onClick = { ActivationStatus.requestScopeRestart() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 8.dp, end = 20.dp),
            )
        }
    }
}
