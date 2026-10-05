package dev.glass.soundbar

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import java.util.WeakHashMap

internal object NativeMaterial {
    private val saved = WeakHashMap<Any, List<Any?>>()
    private data class ForegroundState(val original: Drawable?, val stroke: PanelStrokeDrawable)
    private val foregrounds = WeakHashMap<View, ForegroundState>()

    private class PanelStrokeDrawable(
        private val renderer: Any,
        radius: Float,
        private val cornerWeight: Float
    ) : Drawable() {
        var radius: Float = radius
            set(value) {
                field = value
                invalidateSelf()
            }

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val rect = Rect(bounds)
            Reflect.call(renderer, "draw", canvas, rect, radius, cornerWeight, cornerWeight > 0f, 0)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
    // mVolumeBackgroundBlurDrawable is already created by ColorOS through
    // VolumeBlurManager.getVolumePanelBackground(..., blurRadius = 800, ...).
    // Keep that native panel material intact (mode 4 + ROM theme colors) and
    // only add our resized panel edge stroke. Overriding it with the bar
    // material makes the panel too transparent on bright content.
    fun apply(proxy: Any, root: View, radius: Float) {
        val config = Reflect.call(proxy, "getBlurConfig")!!
        if (!saved.containsKey(config)) {
            saved[config] = listOf(
                Reflect.call(config, "getMotionBlurMixConfig"),
                Reflect.call(config, "getBlurRadius"),
                Reflect.call(config, "getEnableMotionSmoothCorner"),
                Reflect.call(config, "getRadiusWeight")
            )
        }
        applyEdgeStroke(root, radius)
        android.util.Log.i(
            "GlassSoundbar",
            "ColorOS17 native panel material preserved: blur=${Reflect.call(config, "getBlurRadius")} + native edge stroke"
        )
    }

    private fun applyEdgeStroke(root: View, radius: Float) {
        foregrounds[root]?.let {
            it.stroke.radius = radius
            root.invalidate()
            return
        }
        runCatching {
            val hostClass = Class.forName(
                "com.oplus.systemui.volume.utils.material.OplusVolumeSettingsButtonMaterialHost",
                false,
                root.javaClass.classLoader
            )
            val host = hostClass.getConstructor(android.content.Context::class.java).newInstance(root.context)
            Reflect.call(host, "refreshTheme", true)
            val renderer = Reflect.field(host, "strokeRenderer")!!
            val weightId = root.resources.getIdentifier(
                "volume_vertical_row_radius_weight_os17",
                "dimen",
                "com.android.systemui"
            )
            val weight = if (weightId != 0) root.resources.getFloat(weightId) else 0f
            val stroke = PanelStrokeDrawable(renderer, radius, weight)
            val original = root.foreground
            root.foreground = if (original != null) LayerDrawable(arrayOf(original, stroke)) else stroke
            foregrounds[root] = ForegroundState(original, stroke)
            root.invalidate()
        }.onFailure {
            ModuleDebugLog.e("GlassSoundbar", "Failed to attach ColorOS17 edge stroke", it)
        }
    }

    fun detachEdgeStroke(root: View) {
        foregrounds.remove(root)?.let {
            root.foreground = it.original
            root.invalidate()
        }
    }

    fun restore(proxy: Any, root: View) {
        val config = Reflect.call(proxy, "getBlurConfig")!!
        saved.remove(config)?.let {
            Reflect.call(config, "setMotionBlurMixConfig", it[0])
            Reflect.call(config, "setBlurRadius", it[1])
            Reflect.call(config, "setEnableMotionSmoothCorner", it[2])
            Reflect.call(config, "setRadiusWeight", it[3])
            Reflect.call(proxy, "applyBlurConfig")
        }
        detachEdgeStroke(root)
    }
}
