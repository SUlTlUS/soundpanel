package dev.glass.soundbar

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import java.util.WeakHashMap
import java.util.IdentityHashMap
import kotlin.math.roundToInt

internal object NativeMaterial {
    private data class PlatformState(
        val radius: Float, val enabled: Boolean, val stroke: Any?,
        val cornerType: Any?, val cornerRadius: Float, val cornerWeight: Float,
        val optics: Any?, val innerShadow: Any?,
    )
    private val platformCorners = IdentityHashMap<Any, PlatformState>()
    private class PanelBackgroundDrawable(background: Drawable?, tint: GradientDrawable) :
        LayerDrawable(listOfNotNull(background, tint).toTypedArray()) {
        override fun draw(canvas: Canvas) {
            NativePanelMotion.applyBackgroundBounds(this)
            super.draw(canvas)
        }
    }
    private data class ForegroundState(
        val original: Drawable?, val stroke: PanelStrokeDrawable, val tint: GradientDrawable?,
        var originalBackground: Drawable?, var panelBackground: PanelBackgroundDrawable?,
    )
    private val foregrounds = WeakHashMap<View, ForegroundState>()
    private class StrokeFade(val proxy: Any, val config: Any, val platformStroke: NativePanelStroke?) {
        var amount = 1f
        val shaderModes = IdentityHashMap<Any, Boolean>()
        var renderStatus: String? = null
        var renderFailed = false
    }
    private val strokeFades = WeakHashMap<View, StrokeFade>()
    private val drawingFade = ThreadLocal<StrokeFade?>()

    private class PanelStrokeDrawable(
        private val renderer: Any,
        radius: Float,
        private val cornerWeight: Float
    ) : Drawable() {
        private var opacity = 255
        var motionBounds: Rect? = null
        var radius: Float = radius
            set(value) {
                field = value
                invalidateSelf()
            }

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty || opacity == 0) return
            val rect = Rect(motionBounds ?: bounds)
            if (opacity == 255) {
                Reflect.call(renderer, "draw", canvas, rect, radius, cornerWeight, cornerWeight > 0f, 0)
            } else {
                val save = canvas.saveLayerAlpha(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), opacity)
                try {
                    Reflect.call(renderer, "draw", canvas, rect, radius, cornerWeight, cornerWeight > 0f, 0)
                } finally { canvas.restoreToCount(save) }
            }
        }

        override fun setAlpha(alpha: Int) {
            val next = alpha.coerceIn(0, 255)
            if (opacity != next) { opacity = next; invalidateSelf() }
        }
        override fun getAlpha(): Int = opacity
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
    // Both paths use the ROM renderer and native stroke parameters.
    fun apply(proxy: Any, root: View, radius: Float) {
        resetMotionOpacity(root)
        val config = Reflect.call(proxy, "getBlurConfig")!!
        if (DeviceProfile.current == DeviceProfile.ONEPLUS_15) {
            val corner = Reflect.call(config, "getGradientStrokeCornerParam")!!
            platformCorners.getOrPut(config) {
                PlatformState(
                    Reflect.call(config, "getCornerRadius") as Float,
                    Reflect.call(config, "getEnableStaticBlurCorner") as Boolean,
                    Reflect.call(config, "getGradientStrokeLineParam"),
                    Reflect.call(corner, "getType"), Reflect.call(corner, "getRadius") as Float,
                    Reflect.call(corner, "getWeight") as Float,
                    Reflect.call(config, "getOpticsParams"), Reflect.call(config, "getInnerShadowParams"),
                )
            }
            Reflect.call(config, "setCornerRadius", radius)
            Reflect.call(config, "setEnableStaticBlurCorner", true)
            val night = root.resources.configuration.isNightModeActive
            val lightType = Class.forName("com.oplusos.systemui.common.util.GradientStrokeLineAdapter", false, proxy.javaClass.classLoader)
            val light = lightType.getField("INSTANCE").get(null)!!
            // Keep QS button lighting; the collapsed volume background supplies depth/color.
            val template = Reflect.call(light, "getQSPluginBlurLightConfig", night, true)!!
            Reflect.call(light, "adaptStrokeLineParams", config, template)
            Reflect.call(light, "adaptStrokeCornerParams", config, radius, null, true)
            ModuleDebugLog.i("GlassSoundbar", "Panel uses native QS button light: night=$night stroke=${Reflect.call(config, "getGradientStrokeLineParam")}")
        }
        if (DeviceProfile.current?.usesLegacyEdgeStroke == true) {
            applyEdgeStroke(root, radius, proxy.javaClass.classLoader)
        }
        val previous = strokeFades[root]
        strokeFades[root] = StrokeFade(proxy, config, if (DeviceProfile.current == DeviceProfile.ONEPLUS_15) {
            NativePanelStroke(config)
        } else null).also {
            if (previous?.proxy === proxy) it.shaderModes.putAll(previous.shaderModes)
        }
        ModuleDebugLog.i(
            "GlassSoundbar",
            "Native panel material preserved: profile=${DeviceProfile.current} blur=${Reflect.call(config, "getBlurRadius")}"
        )
    }

    private fun applyEdgeStroke(root: View, radius: Float, loader: ClassLoader?) {
        foregrounds[root]?.let {
            it.stroke.radius = radius
            it.tint?.cornerRadius = radius
            if (it.tint != null && root.background !== it.panelBackground) {
                // expandPanel replaces the background after our pre-layout pass.
                // Wrap the newly installed native blur, not the earlier empty background.
                it.originalBackground = root.background
                it.panelBackground = PanelBackgroundDrawable(it.originalBackground, it.tint)
                root.background = it.panelBackground
                ModuleDebugLog.i("GlassSoundbar", "Ace 6 panel tint rebound after native background replacement")
            }
            root.invalidate()
            return
        }
        runCatching {
            val hostClass = Class.forName(
                "com.oplus.systemui.volume.utils.material.OplusVolumeSettingsButtonMaterialHost",
                false,
                loader
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
            val tint = NativeVolumeSurface.panelTint(radius)
            val original = root.foreground
            // Foreground paints after the sliders. Keep the native tint behind
            // the content, alongside the blur; only the edge light belongs above.
            val background = root.background
            val panelBackground = tint?.let { PanelBackgroundDrawable(background, it) }
            if (panelBackground != null) root.background = panelBackground
            val layers = listOfNotNull(original, stroke)
            root.foreground = if (layers.size == 1) stroke else LayerDrawable(layers.toTypedArray())
            foregrounds[root] = ForegroundState(original, stroke, tint, background, panelBackground)
            root.invalidate()
        }.onFailure {
            ModuleDebugLog.e("GlassSoundbar", "Failed to attach ColorOS17 edge stroke", it)
        }
    }

    fun setMotionBounds(root: View, bounds: Rect?) {
        foregrounds[root]?.tint?.bounds = bounds ?: Rect(0, 0, root.width, root.height)
        foregrounds[root]?.stroke?.let {
            if (it.motionBounds != bounds) {
                if (bounds == null) it.motionBounds = null
                else it.motionBounds?.set(bounds) ?: run { it.motionBounds = Rect(bounds) }
                it.invalidateSelf()
            }
        }
    }

    /** Use the ROM's existing blur fade, independently of the width morph. */
    fun syncMotionOpacity(root: View) {
        val fade = strokeFades[root] ?: return
        val amount = (Reflect.call(fade.proxy, "getBlurAmount") as Float).coerceIn(0f, 1f)
        if (fade.amount == amount) return
        foregrounds[root]?.stroke?.alpha = (amount * 255f).roundToInt()
        foregrounds[root]?.tint?.alpha = (amount * 255f).roundToInt()
        fade.platformStroke?.let {
            it.restore(amount)
            syncPlatformStroke(fade.proxy)
        }
        fade.amount = amount
    }

    fun resetMotionOpacity(root: View) {
        val fade = strokeFades[root] ?: return
        if (fade.amount == 1f) return
        foregrounds[root]?.stroke?.alpha = 255
        foregrounds[root]?.tint?.alpha = 255
        fade.platformStroke?.let {
            it.restore(1f)
            syncPlatformStroke(fade.proxy)
        }
        fade.amount = 1f
    }

    fun restoreAfterNativeRefresh(config: Any): Boolean {
        val fade = strokeFades.values.firstOrNull { it.config === config } ?: return false
        val stroke = fade.platformStroke ?: return false
        val amount = (Reflect.call(fade.proxy, "getBlurAmount") as Float).coerceIn(0f, 1f)
        stroke.restore(amount)
        fade.amount = amount
        return true
    }

    fun drawBackground(background: Drawable, original: () -> Any?): Any? {
        if (DeviceProfile.current != DeviceProfile.ONEPLUS_15) return original()
        val root = NativePanelLayout.activeRoot ?: return original()
        if (root.background !== background) return original()
        val fade = strokeFades[root] ?: return original()
        val previous = drawingFade.get()
        drawingFade.set(fade)
        return try { original() } finally { drawingFade.set(previous) }
    }

    /** The ROM has already selected and prepared this renderer; do not prepare it twice. */
    fun beforePlatformDraw(actual: Any, canvas: Canvas) {
        val fade = drawingFade.get() ?: return
        val stroke = fade.platformStroke ?: return
        if (fade.renderFailed) return
        runCatching {
            val renderer = Reflect.call(actual, "getBlurDrawable")!!
            if (!fade.shaderModes.containsKey(renderer)) {
                val shader = Reflect.call(renderer, "getDrawableShader")!!
                ModuleDebugLog.i("GlassSoundbar", "Native panel stroke before direct setup: renderer=${renderer.javaClass.name} shader=${Reflect.call(renderer, "getEnableShader")} stroke=${Reflect.call(shader, "getGradientStrokeLineParams")} corner=${Reflect.call(shader, "getCornerParams")} configured=${Reflect.call(fade.config, "getGradientStrokeLineParam")}")
            }
            fade.shaderModes.getOrPut(renderer) { Reflect.call(renderer, "getEnableShader") as Boolean }
            val amount = (Reflect.call(fade.proxy, "getBlurAmount") as Float).coerceIn(0f, 1f)
            stroke.applyTo(renderer, amount)
            fade.amount = amount
            val stage = when { amount <= 0f -> "hidden"; amount >= 0.99f -> "shown"; else -> "fading" }
            val status = "$stage:${System.identityHashCode(renderer)}"
            if (fade.renderStatus != status) {
                fade.renderStatus = status
                val shader = Reflect.call(renderer, "getDrawableShader")!!
                ModuleDebugLog.i("GlassSoundbar", "Native panel stroke draw: stage=$stage hardware=${canvas.isHardwareAccelerated} amount=$amount bounds=${(actual as Drawable).bounds} shader=${Reflect.call(renderer, "getEnableShader")} valid=${Reflect.call(shader, "getShaderOrNull") != null} alpha=${Reflect.call(renderer, "getPaintAlpha")} stroke=${Reflect.call(shader, "getGradientStrokeLineParams")} corner=${Reflect.call(shader, "getCornerParams")}")
            }
        }.onFailure {
            fade.renderFailed = true
            ModuleDebugLog.e("GlassSoundbar", "Native panel stroke renderer setup failed", it)
        }
    }

    fun detachEdgeStroke(root: View) {
        resetMotionOpacity(root)
        strokeFades.remove(root)?.shaderModes?.forEach { (renderer, enabled) ->
            Reflect.call(renderer, "setEnableBlurShader", enabled)
        }
        foregrounds.remove(root)?.let {
            root.foreground = it.original
            if (root.background === it.panelBackground) root.background = it.originalBackground
            root.invalidate()
        }
    }

    fun restorePlatformCorners(config: Any) {
        platformCorners.remove(config)?.let { original ->
            Reflect.call(config, "setCornerRadius", original.radius)
            Reflect.call(config, "setEnableStaticBlurCorner", original.enabled)
            Reflect.call(config, "setGradientStrokeLineParam", original.stroke)
            Reflect.call(config, "setOpticsParams", original.optics)
            Reflect.call(config, "setInnerShadowParams", original.innerShadow)
            val corner = Reflect.call(config, "getGradientStrokeCornerParam")!!
            Reflect.call(corner, "setType", original.cornerType)
            Reflect.call(corner, "setRadius", original.cornerRadius)
            Reflect.call(corner, "setWeight", original.cornerWeight)
        }
    }

    fun syncPlatformStroke(proxy: Any) {
        if (DeviceProfile.current != DeviceProfile.ONEPLUS_15) return
        val drawable = Reflect.call(proxy, "getCachePlatformStaticBlurDrawable") ?: return
        val managerType = Class.forName(
            "com.oplus.systemui.volume.utils.material.VolumeBlurManager", false, proxy.javaClass.classLoader,
        )
        val manager = managerType.getField("INSTANCE").get(null)!!
        Reflect.call(manager, "hotUpdateStaticPlatformLightAndStroke", drawable, Reflect.call(proxy, "getBlurConfig"))
        Reflect.call(drawable, "invalidateSelf")
    }

}
