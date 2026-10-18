package dev.glass.soundbar

/** Persist ARGB independently; null follows the ROM, including theme changes. */
internal enum class VolumeIconStream(val stream: Int, val title: String, val key: String) {
    MEDIA(3, "媒体", "media_icon_color"),
    RINGER(2, "铃声", "ringer_icon_color"),
    NOTIFICATION(5, "通知", "notification_icon_color"),
    ALARM(4, "闹钟", "alarm_icon_color"),
    CALL(0, "通话", "call_icon_color");

    companion object {
        fun fromStream(stream: Int): VolumeIconStream? = entries.firstOrNull {
            it.stream == stream || (it == CALL && stream == 6)
        }
    }

    fun color(settings: PanelSettings): Int? = when (this) {
        MEDIA -> settings.mediaIconColor
        RINGER -> settings.ringerIconColor
        ALARM -> settings.alarmIconColor
        NOTIFICATION -> settings.notificationIconColor
        CALL -> settings.callIconColor
    }

    fun withColor(settings: PanelSettings, color: Int?): PanelSettings = when (this) {
        MEDIA -> settings.copy(mediaIconColor = color)
        RINGER -> settings.copy(ringerIconColor = color)
        ALARM -> settings.copy(alarmIconColor = color)
        NOTIFICATION -> settings.copy(notificationIconColor = color)
        CALL -> settings.copy(callIconColor = color)
    }
}
