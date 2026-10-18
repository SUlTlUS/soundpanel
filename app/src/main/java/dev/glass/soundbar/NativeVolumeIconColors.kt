package dev.glass.soundbar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import java.util.WeakHashMap

/** Filter the final animated icon drawing, so ROM JSON colors and reloads cannot replace it. */
internal object NativeVolumeIconColors {
    private val paints = WeakHashMap<View, Paint>()

    fun update(row: Any, configuration: PanelSettings? = null) {
        val icon = Reflect.call(row, "getIcon") as? View ?: return
        val stream = Reflect.call(row, "getStream") as Int
        val settings = configuration ?: PanelSettings.read(icon.context)
        val color = if (settings.enabled) VolumeIconStream.fromStream(stream)?.color(settings) else null
        if (color == null) paints.remove(icon)
        else paints[icon] = Paint().apply { colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }
        icon.invalidate()
    }

    fun draw(icon: View, canvas: Canvas, original: () -> Any?): Any? {
        val paint = paints[icon] ?: return original()
        // Scope the filter to this draw only. No native drawable, tint or animation state is changed.
        val save = canvas.saveLayer(0f, 0f, icon.width.toFloat(), icon.height.toFloat(), paint)
        return try { original() } finally { canvas.restoreToCount(save) }
    }
}
