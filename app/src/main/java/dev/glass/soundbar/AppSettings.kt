package dev.glass.soundbar

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

internal object LauncherIcon {
    private fun component(context: Context) = ComponentName(context, "${context.packageName}.Launcher")

    fun isVisible(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setVisible(context: Context, visible: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

internal object AppAppearance {
    fun liquidGlass(context: Context): Boolean =
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getBoolean("liquid_glass", true)

    fun setLiquidGlass(context: Context, enabled: Boolean) {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("liquid_glass", enabled).apply()
    }
}

internal enum class HookLogLevel(val title: String, val priority: Int) {
    OFF("关闭", Int.MAX_VALUE),
    INFO("普通", Log.INFO),
    DEBUG("调试", Log.DEBUG),
    VERBOSE("详细", Log.VERBOSE);

    companion object {
        fun fromPriority(priority: Int) = entries.firstOrNull { it.priority == priority } ?: INFO
    }
}
