package dev.glass.soundbar

/** Only the automatic side sliders are suppressed; the manual app-volume panel stays native. */
internal object MultiAppVolumePolicy {
    fun hides(settings: PanelSettings): Boolean = settings.enabled && settings.hideMultiAppVolume

    fun hidesRow(settings: PanelSettings, expanded: Boolean, isMultiAppRow: Boolean): Boolean =
        hides(settings) && !expanded && isMultiAppRow

    fun <T> visibleRows(rows: List<T>, settings: PanelSettings, expanded: Boolean, isMultiAppRow: (T) -> Boolean): List<T> =
        if (!hides(settings) || expanded) rows else rows.filterNot(isMultiAppRow)
}
