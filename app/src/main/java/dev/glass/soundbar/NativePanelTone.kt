package dev.glass.soundbar

import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/** Adjust the ROM's panel mix colors without replacing its blur drawable. */
internal object NativePanelTone {
    private data class Original(val color: Int, val platform: Any?, val motion: Any?)
    // BlurConfig is a mutable data class: its hash changes as colors change.
    private val originals = IdentityHashMap<Any, Original>()

    fun apply(config: Any, amount: Float) {
        if (amount == 0f && config !in originals) return
        val original = originals.getOrPut(config) {
            Original(
                Reflect.call(config, "getBlurColor") as Int,
                Reflect.call(config, "getPlatformMixConfig"),
                Reflect.call(config, "getMotionBlurMixConfig"),
            )
        }
        Reflect.call(config, "setBlurColor", adjustColor(original.color, amount))
        Reflect.call(config, "setPlatformMixConfig", adjustMix(original.platform, amount))
        Reflect.call(config, "setMotionBlurMixConfig", adjustMix(original.motion, amount))
    }

    fun restore(config: Any) {
        originals.remove(config)?.let {
            Reflect.call(config, "setBlurColor", it.color)
            Reflect.call(config, "setPlatformMixConfig", it.platform)
            Reflect.call(config, "setMotionBlurMixConfig", it.motion)
        }
    }

    fun adjustMix(mix: Any?, amount: Float, fade: Boolean? = null, copy: Boolean = false): Any? {
        if (mix == null || (amount == 0f && fade == null && !copy)) return mix
        // R8-built SystemUI can omit nested-class metadata. simpleName then
        // includes the outer name, although the getters/copy contract is intact.
        val methods = mix.javaClass.methods
        if (methods.any { it.name == "getForegroundShaderParam" } &&
            methods.any { it.name == "getBackgroundShaderParam" } &&
            methods.any { it.name == "copy" && it.parameterCount == 5 }) {
            return Reflect.call(mix, "copy",
                adjustShaderColor(Reflect.call(mix, "getForegroundShaderParam")!!, amount),
                adjustShaderColor(Reflect.call(mix, "getBackgroundShaderParam")!!, amount),
                Reflect.call(mix, "getMirrorScale"), fade ?: Reflect.call(mix, "getAlphaWithBlurAmount"),
                adjustColor(Reflect.call(mix, "getPlaceHolderColor") as Int, amount))
        }
        val getter = methods.firstOrNull { read ->
            read.parameterCount == 0 && (read.name == "getMixColor" || read.name == "getBackgroundShaderParam") &&
                methods.any { it.name == "copy" && it.parameterTypes.contentEquals(arrayOf(read.returnType)) }
        } ?: return mix
        val color = Reflect.call(mix, getter.name)!!
        val top = adjustColor(Reflect.call(color, "getTopLayerColor") as Int, amount)
        val bottom = adjustColor(Reflect.call(color, "getBottomLayerColor") as Int, amount)
        val adjusted = if (getter.name == "getMixColor") {
            Reflect.call(color, "copy", Reflect.call(color, "getMode"), top, bottom)
        } else {
            Reflect.call(color, "copy", Reflect.call(color, "getTopMode"), top, Reflect.call(color, "getBottomMode"), bottom)
        }
        return Reflect.call(mix, "copy", adjusted)!!.also { copy ->
            Reflect.call(copy, "setAlphaWithBlurAmount", fade ?: Reflect.call(mix, "getAlphaWithBlurAmount"))
            Reflect.call(copy, "setMirrorScale", Reflect.call(mix, "getMirrorScale"))
            Reflect.call(copy, "setPlaceHolderColor", adjustColor(Reflect.call(mix, "getPlaceHolderColor") as Int, amount))
        }
    }

    fun adjustColor(color: Int, amount: Float): Int {
        if (color ushr 24 == 0 || amount == 0f) return color
        val target = if (amount > 0f) 255 else 0
        val weight = abs(amount).coerceAtMost(1f)
        val originalAlpha = color ushr 24
        // Composite black into the native tint. Preserving alpha made a black
        // luminosity layer unchanged, so moving the slider darker had no effect.
        val alpha = if (amount < 0f) originalAlpha + (255 - originalAlpha) * weight else originalAlpha.toFloat()
        val colorWeight = if (amount < 0f) weight * 255f / alpha else weight
        fun channel(shift: Int): Int {
            val value = (color ushr shift) and 255
            return (value + (target - value) * colorWeight).roundToInt() shl shift
        }
        return (alpha.roundToInt() shl 24) or channel(16) or channel(8) or channel(0)
    }

    private fun adjustShaderColor(color: Any, amount: Float): Any = Reflect.call(color, "copy",
        Reflect.call(color, "getTopMode"), adjustColor(Reflect.call(color, "getTopLayerColor") as Int, amount),
        Reflect.call(color, "getBottomMode"), adjustColor(Reflect.call(color, "getBottomLayerColor") as Int, amount))!!
}
