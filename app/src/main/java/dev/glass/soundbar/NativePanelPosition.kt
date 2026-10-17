package dev.glass.soundbar

import android.view.View
import android.view.WindowInsets

/** Move the native root, so sliders, accessory controls and touch bounds move together. */
internal object NativePanelPosition {
    fun attach(native: Any) {
        val root = Reflect.field(native, "mDialogView") as View
        val recycler = Reflect.field(native, "mVolumeRecyclerview") as View
        val rail = (Reflect.field(native, "mMoreRowStreamLl") as? View)?.parent as? View
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (NativePanelLayout.activeRoot !== root) apply(native, PanelSettings.read(root.context))
        }
        root.addOnLayoutChangeListener(listener)
        recycler.addOnLayoutChangeListener(listener)
        rail?.addOnLayoutChangeListener(listener)
    }

    fun apply(native: Any, settings: PanelSettings) {
        val root = Reflect.field(native, "mDialogView") as View
        if (NativePanelLayout.activeRoot === root) return
        val amount = if (settings.enabled) settings.positionFor(root.context) else 0f
        if (amount == 0f) {
            root.translationY = 0f
            return
        }
        val recycler = Reflect.field(native, "mVolumeRecyclerview") as View
        val parent = root.parent as? View ?: return
        if (!root.isLaidOut || !recycler.isLaidOut || parent.height <= 0) return
        val parentLocation = IntArray(2)
        parent.getLocationOnScreen(parentLocation)
        val location = IntArray(2)
        recycler.getLocationOnScreen(location)
        val nativeTop = location[1] - root.translationY
        var nativeBottom = nativeTop + recycler.height
        val rail = (Reflect.field(native, "mMoreRowStreamLl") as? View)?.parent as? View
        if (rail != null && rail.visibility == View.VISIBLE && rail.isLaidOut) {
            rail.getLocationOnScreen(location)
            nativeBottom = maxOf(nativeBottom, location[1] - root.translationY + rail.height)
        }
        val insets = root.rootWindowInsets?.getInsets(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        val window = root.rootView
        window.getLocationOnScreen(location)
        val edge = 12f * root.resources.displayMetrics.density
        val safeTop = maxOf(parentLocation[1], location[1] + (insets?.top ?: 0)) + edge
        val safeBottom = minOf(parentLocation[1] + parent.height,
            location[1] + window.height - (insets?.bottom ?: 0)) - edge
        val offset = if (amount < 0f) {
            -amount * (safeTop - nativeTop).coerceAtMost(0f)
        } else amount * (safeBottom - nativeBottom).coerceAtLeast(0f)
        if (kotlin.math.abs(root.translationY - offset) > 0.5f) {
            root.translationY = offset
            ModuleDebugLog.i("GlassSoundbar", "Vertical position: orientation=${root.resources.configuration.orientation} amount=$amount offset=$offset")
        }
    }
}
