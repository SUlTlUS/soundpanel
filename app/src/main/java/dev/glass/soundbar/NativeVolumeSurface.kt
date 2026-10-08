package dev.glass.soundbar

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import java.util.IdentityHashMap

/** Keep separate collapsed palettes for the track and the filled progress layer. */
internal object NativeVolumeSurface {
    private data class Binding(val proxy: Any, val surface: NativePanelSurface)
    private var baseline: NativeSurfacePreset? = null
    private var progressBaseline: NativeSurfacePreset? = null
    private var tone = 0f
    private val bindings = IdentityHashMap<Any, Binding>()
    private val backgrounds = IdentityHashMap<Drawable, Any>()
    private var panelTint: GradientDrawable? = null

    fun panelTint(radius: Float): GradientDrawable? = panelTint?.constantState?.newDrawable()?.mutate()?.let {
        (it as GradientDrawable).apply {
            setColor(NativeSurfacePreset.panelColor(panelTint!!.color!!.defaultColor, tone))
            cornerRadius = radius
        }
    }

    fun capture(slider: View, amount: Float, panelProxy: Any?) {
        // Repeated expand requests must not capture our own toned material.
        if (baseline != null) return
        tone = amount
        val proxy = blurProxy(slider)
        baseline = if (proxy != null) {
            NativeSurfacePreset.capture(Reflect.call(proxy, "getBlurConfig")!!)
        } else if (DeviceProfile.current == DeviceProfile.ACE_6 && panelProxy != null) {
            legacyBaseline(slider, panelProxy)
        } else null
        progressDrawable(slider)?.let { drawable ->
            val progressProxy = Reflect.call(drawable, "getViewBlurProxy")!!
            progressBaseline = NativeSurfacePreset.capture(Reflect.call(progressProxy, "getBlurConfig")!!)
            ModuleDebugLog.i("GlassSoundbar", "Collapsed progress baseline: color=${Integer.toHexString(progressBaseline!!.color)} platform=${progressBaseline!!.platformMix}")
        }
        baseline?.let {
            ModuleDebugLog.i("GlassSoundbar", "Collapsed surface baseline: radius=${it.radius} color=${Integer.toHexString(it.color)} platform=${it.platformMix} motion=${it.motionMix} tone=$tone")
        } ?: ModuleDebugLog.w("GlassSoundbar", "Collapsed surface has no supported blur material; retaining native surfaces")
    }

    fun bindPanel(proxy: Any): Boolean = baseline?.let { bind(proxy, it, panel = true, role = "panel") } ?: false

    fun bindSlider(slider: View) {
        val preset = baseline ?: return
        autoBlur(slider)?.let { bindDrawable(it, preset, "slider") }
        progressBaseline?.let { progress ->
            progressDrawable(slider)?.let { bindDrawable(it, progress, "progress") }
        }
    }

    private fun bindDrawable(drawable: Drawable, preset: NativeSurfacePreset, role: String) {
        val proxy = Reflect.call(drawable, "getViewBlurProxy")!!
        backgrounds[drawable] = Reflect.call(proxy, "getBlurConfig")!!
        bind(proxy, preset, panel = false, role = role)
    }

    private fun bind(proxy: Any, preset: NativeSurfacePreset, panel: Boolean, role: String): Boolean {
        val config = Reflect.call(proxy, "getBlurConfig")!!
        if (bindings.containsKey(config)) return true
        NativePanelTone.restore(config)
        val surface = NativePanelSurface(config)
        surface.setPreset(preset, tone, panel)
        bindings[config] = Binding(proxy, surface)
        ModuleDebugLog.i("GlassSoundbar", "Surface bound: role=$role config=${System.identityHashCode(config)} mix=${System.identityHashCode(Reflect.call(config, "getPlatformMixConfig"))} tone=${if (panel) tone else 0f} color=${Integer.toHexString(Reflect.call(config, "getBlurColor") as Int)}")
        Reflect.call(proxy, "applyBlurConfig")
        return true
    }

    fun restoreAfterNativeRefresh(config: Any): Boolean {
        val binding = bindings[config] ?: return false
        binding.surface.apply()
        return true
    }

    fun beforeDraw(drawable: Drawable) {
        val config = backgrounds[drawable] ?: return
        val binding = bindings[config] ?: return
        // Reuse prepared mixes instead of cloning shader layers on every animation frame.
        if (binding.surface.apply()) Reflect.call(binding.proxy, "applyBlurConfig")
    }

    fun restore() {
        backgrounds.clear()
        val saved = bindings.values.toList()
        bindings.clear()
        baseline = null
        progressBaseline = null
        panelTint = null
        saved.forEach {
            it.surface.restore()
            Reflect.call(it.proxy, "applyBlurConfig")
        }
    }

    private fun finder(slider: View): Any {
        val type = Class.forName("com.oplus.systemui.volume.utils.material.OplusVolumeBlurDrawableFinder", false, slider.javaClass.classLoader)
        return type.getField("INSTANCE").get(null)!!
    }

    private fun autoBlur(slider: View): Drawable? =
        Reflect.call(finder(slider), "findAutoBlurDrawable", slider.background) as? Drawable

    private fun blurProxy(slider: View): Any? = autoBlur(slider)?.let { Reflect.call(it, "getViewBlurProxy") }

    private fun progressDrawable(slider: View): Drawable? =
        if (DeviceProfile.current == DeviceProfile.ONEPLUS_15) Reflect.field(slider, "mProgressBlurDrawable") as? Drawable
        else null

    private fun legacyTint(slider: View): GradientDrawable? {
        val wrapped = slider.background ?: return null
        val content = if (wrapped.javaClass.methods.any { it.name == "getContent" && it.parameterCount == 0 }) {
            Reflect.call(wrapped, "getContent") as? Drawable
        } else wrapped
        val layer = content as? LayerDrawable ?: return null
        if (layer.numberOfLayers < 2) return null
        if (Reflect.call(finder(slider), "findBackgroundBlurDrawable", wrapped) == null) return null
        return (layer.getDrawable(1) as? GradientDrawable)?.takeIf { it.color != null }
    }

    private fun legacyBaseline(slider: View, panelProxy: Any): NativeSurfacePreset? {
        val tint = legacyTint(slider) ?: return null
        panelTint = tint.constantState?.newDrawable()?.mutate() as? GradientDrawable
        val loader = slider.javaClass.classLoader
        val managerType = Class.forName("com.oplus.systemui.volume.utils.material.VolumeBlurManager", false, loader)
        val manager = managerType.getField("INSTANCE").get(null)!!
        val radius = Reflect.call(manager, "getVolumeBarBlurStrengthMaxPx", slider.context) as Int
        val colorType = Class.forName("com.oplusos.systemui.common.blurability.MixColor", false, loader)
        // Exact Ace 6 getVolumeBarBackground arrays: bottom 0, top RGBA 0.6.
        val color = colorType.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType).newInstance(0, 0x99999999.toInt(), 0)
        val mixType = Class.forName("com.oplusos.systemui.common.blurability.BlurMixConfig\$BlurMixSingle", false, loader)
        val mix = mixType.getConstructor(colorType).newInstance(color)
        val panel = NativeSurfacePreset.capture(Reflect.call(panelProxy, "getBlurConfig")!!)
        return panel.copy(
            radius = radius, motionRadius = panel.motionRadius?.let { radius }, color = 0, motionMix = mix,
        )
    }

}
