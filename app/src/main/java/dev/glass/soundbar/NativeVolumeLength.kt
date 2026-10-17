package dev.glass.soundbar

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Resize the native row container; seekbar drawing and input use its measured size. */
internal object NativeVolumeLength {
    private data class Baseline(val height: Int, val topMargin: Int)
    private val baselines = WeakHashMap<View, Baseline>()

    fun restore(native: Any): Boolean {
        val recycler = Reflect.field(native, "mVolumeRecyclerview") as? View ?: return false
        val baseline = baselines.remove(recycler) ?: return false
        val lp = recycler.layoutParams as ViewGroup.MarginLayoutParams
        if (lp.height == baseline.height && lp.topMargin == baseline.topMargin) return false
        lp.height = baseline.height
        lp.topMargin = baseline.topMargin
        recycler.layoutParams = lp
        return true
    }

    /** Finish resizing and positioning before the first visible frame. */
    fun prepareShow(native: Any, settings: PanelSettings) {
        val root = Reflect.field(native, "mDialogView") as View
        val recycler = Reflect.field(native, "mVolumeRecyclerview") as View
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean = try {
                if (NativePanelLayout.activeNative !== native &&
                    (apply(native, settings) || recycler.isLayoutRequested || syncSideRows(native))) {
                    false
                } else {
                    root.viewTreeObserver.removeOnPreDrawListener(this)
                    NativePanelPosition.apply(native, settings)
                    true
                }
            } catch (error: Exception) {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                ModuleDebugLog.e("GlassSoundbar", "Volume length first-frame layout failed", error)
                true
            }
        })
    }

    private fun syncSideRows(native: Any): Boolean {
        val adapter = Reflect.field(native, "volumeListAdapter") ?: return false
        val rows = Reflect.call(adapter, "getSideShowList") as List<*>
        var changed = false
        for (row in rows) {
            val view = Reflect.call(row!!, "getView") as? View ?: continue
            val host = view.parent as? android.widget.FrameLayout ?: continue
            val lp = view.layoutParams as android.widget.FrameLayout.LayoutParams
            if (host.height <= 0 || lp.topMargin >= 0 || lp.height == host.height - lp.topMargin) continue
            val layoutType = Class.forName("com.oplus.systemui.volume.utils.VolumeLayoutUtil", false, native.javaClass.classLoader)
            val layout = layoutType.getField("INSTANCE").get(null)!!
            // The ROM caches the row height at bind time; repeat its layout with the resized host.
            Reflect.call(layout, "prepareSideVolumeProgressOverflow", view, host)
            ModuleDebugLog.d("GlassSoundbar", "Volume row length synced: host=${host.height} row=${view.height}->${lp.height} overflow=${-lp.topMargin}")
            changed = true
        }
        return changed
    }

    fun apply(native: Any, settings: PanelSettings): Boolean {
        if (NativePanelLayout.activeNative === native) return false
        if (!settings.enabled || settings.lengthScale == 1f) {
            return restore(native)
        }
        val recycler = Reflect.field(native, "mVolumeRecyclerview") as? View ?: return false
        val lp = recycler.layoutParams as ViewGroup.MarginLayoutParams
        if (lp.height <= 0) return false
        val baseline = baselines.getOrPut(recycler) { Baseline(lp.height, lp.topMargin) }
        val resources = recycler.resources
        fun dimension(name: String): Int = resources.getDimensionPixelSize(
            resources.getIdentifier(name, "dimen", "com.android.systemui"),
        )
        // Labels and elevation insets keep their own size; only the capsule changes length.
        val fixed = dimension("volume_row_text_height") +
            dimension("oplus_volume_elevation_padding_top") + dimension("oplus_volume_elevation_padding_bottom")
        val capsule = (baseline.height - fixed).coerceAtLeast(1)
        val root = Reflect.field(native, "mDialogView") as View
        val rail = (Reflect.field(native, "mMoreRowStreamLl") as? View)?.parent as? View
        val windowHeight = (root.parent as? View)?.height?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val insets = root.rootWindowInsets
            ?.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
        val available = windowHeight - (insets?.top ?: 0) - (insets?.bottom ?: 0) -
            (rail?.height ?: 0) - (24f * resources.displayMetrics.density).roundToInt()
        val height = fixed + (capsule * settings.lengthScale).roundToInt()
            .coerceAtMost((available - fixed).coerceAtLeast(capsule))
        val topMargin = if (baseline.topMargin > 0) {
            (baseline.topMargin - (height - baseline.height) / 2).coerceAtLeast(0)
        } else baseline.topMargin
        if (lp.height == height && lp.topMargin == topMargin) return false
        lp.height = height
        lp.topMargin = topMargin
        recycler.layoutParams = lp
        ModuleDebugLog.d("GlassSoundbar", "Volume length scale=${settings.lengthScale} native=${baseline.height} height=$height")
        return true
    }
}
