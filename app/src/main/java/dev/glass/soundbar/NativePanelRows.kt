package dev.glass.soundbar

import android.content.Context

/** Extend only the native panel's truncated display cache, before its differ snapshot is copied. */
internal object NativePanelRows {
    class Selection(val original: ArrayList<Any>, val expanded: CompleteSystemPanelList<Any>)

    fun retainAlarmVisibility(
        enabled: Boolean, expanded: Boolean, stream: Int, important: Boolean,
        callMode: Boolean, multiApp: Boolean, accessibility: Boolean,
    ): Boolean = enabled && expanded && stream == 4 && important && callMode && !multiApp && !accessibility

    fun shouldKeepAlarm(dialog: Any, row: Any?, expanded: Boolean): Boolean {
        if (!expanded || row == null || Reflect.call(row, "getStream") != 4) return false
        val settings = PanelSettings.read(Reflect.field(dialog, "mContext") as Context)
        if (!settings.enabled) return false
        val repository = Reflect.field(dialog, "mAudioDataRepository") ?: return false
        val activeStream = Reflect.field(dialog, "mActiveStream") as Int
        // The ROM explicitly excludes alarm before it sets isShowInPanel. Keep
        // its ordinary important-row policy, removing only the call-mode exclusion.
        return retainAlarmVisibility(settings.enabled, expanded, 4,
            Reflect.call(row, "getImportant") == true,
            Reflect.call(repository, "isAudioCallMode", activeStream) == true,
            Reflect.field(row, "isMultiMediaVolume") == true,
            Reflect.field(dialog, "mShowA11yStream") == true)
    }

    fun needsExtraRows(streams: List<Int>): Boolean =
        streams.size > 4 && 4 in streams && streams.any { it == 0 || it == 6 }

    fun keepsAll(rows: List<Any>): Boolean = needsExtraRows(rows.map { Reflect.call(it, "getStream") as Int })

    fun prepare(adapter: Any): Selection? {
        if (Reflect.field(adapter, "isSystemPanel") != true) return null
        if (!PanelSettings.read(Reflect.field(adapter, "context") as Context).enabled) return null
        val visible = (Reflect.call(adapter, "getVolumeRowList") as List<*>).filterNotNull().filter {
            Reflect.field(it, "isShowInPanel") == true && Reflect.field(it, "isMultiMediaVolume") != true
        }
        if (!keepsAll(visible)) return null
        @Suppress("UNCHECKED_CAST")
        val original = Reflect.field(adapter, "panelShowList") as ArrayList<Any>
        val expanded = CompleteSystemPanelList(original, visible)
        Reflect.set(adapter, "panelShowList", expanded)
        return Selection(original, expanded)
    }

    fun finish(adapter: Any, selection: Selection) {
        try {
            selection.original.clear()
            selection.original.addAll(selection.expanded)
        } finally {
            Reflect.set(adapter, "panelShowList", selection.original)
        }
    }
}

internal class CompleteSystemPanelList<T>(original: Collection<T>, private val visible: List<T>) : ArrayList<T>(original) {
    override fun addAll(elements: Collection<T>): Boolean {
        val changed = super.addAll(elements)
        // Both supported ROMs use addAll(sortedRows.subList(0, 4)). Retain that
        // native prefix/order and append only eligible rows omitted by its cap.
        if (elements.size != 4 || size != 4 || visible.size <= 4 || !visible.containsAll(elements)) return changed
        return super.addAll(visible.filterNot { contains(it) }) || changed
    }
}
