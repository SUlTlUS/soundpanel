package dev.glass.soundbar

import android.content.Context

/** GameSpace draws its own MultiVolumeManager window, independently of SystemUI rows. */
internal object GameMultiAppVolume {
    const val packageName = "com.oplus.games"
    const val automaticShow = "com.oplus.games.legacy.multivolume.MultiVolumeManager\$showMultiVolumeViewForUser\$1"

    fun shouldSuppress(coroutine: Any, result: Any?): Boolean {
        // Return the target app's own initial Unit, preserving its coroutine/Job contract.
        // Never intercept resumption after suspension or swallow a cancellation/failure.
        if (Reflect.field(coroutine, "label") != 0 ||
            result?.javaClass?.name !in setOf("kotlin.Unit", "kotlin.q")) return false
        val manager = Reflect.field(coroutine, "this\$0")!!
        val contextGetter = manager.javaClass.declaredMethods.single {
            it.parameterCount == 0 && it.returnType == Context::class.java
        }.apply { isAccessible = true }
        val context = contextGetter.invoke(manager) as Context
        ModuleDebugLog.initialize(context)
        return MultiAppVolumePolicy.hides(PanelSettings.read(context))
    }
}
