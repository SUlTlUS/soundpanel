package dev.glass.soundbar

import android.content.Context

internal object NativeMultiAppVolume {
    private fun isMultiAppRow(row: Any?): Boolean =
        row != null && Reflect.field(row, "isMultiMediaVolume") == true

    fun shouldHideRow(dialog: Any, row: Any?, expanded: Boolean): Boolean {
        if (expanded || !isMultiAppRow(row)) return false
        val settings = PanelSettings.read(Reflect.field(dialog, "mContext") as Context)
        return MultiAppVolumePolicy.hidesRow(settings, expanded, true)
    }

    fun filterSideRows(adapter: Any): Boolean {
        val settings = PanelSettings.read(Reflect.field(adapter, "context") as Context)
        if (!MultiAppVolumePolicy.hides(settings)) return false
        // This is a display cache copied into the differ after filterSideList returns.
        // Never filter volumeRowList, repository rows, or the manual panelShowList.
        @Suppress("UNCHECKED_CAST")
        val rows = Reflect.field(adapter, "sideShowList") as MutableList<Any?>
        val visible = MultiAppVolumePolicy.visibleRows(rows, settings, false, ::isMultiAppRow)
        if (visible.size == rows.size) return false
        val removed = rows.size - visible.size
        rows.clear()
        rows.addAll(visible)
        ModuleDebugLog.d("GlassSoundbar", "Hidden automatic app-volume sliders: $removed; retained=${rows.size}")
        return true
    }
}
