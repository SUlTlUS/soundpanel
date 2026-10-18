package dev.glass.soundbar

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class VolumeIconColorsTest {
    @Test fun upgradeKeepsAllFiveNativeColors() {
        val settings = PanelSettings.fromValues(mapOf("enabled" to true, "tone" to 0.25f))
        VolumeIconStream.entries.forEach { assertNull(it.color(settings)) }
        assertEquals(listOf(3, 2, 5, 4, 0), VolumeIconStream.entries.map { it.stream })
    }

    @Test fun persistedArgbAndDefaultResetSurviveTheSameWriterUsedByRemoteAndSnapshot() {
        val values = mutableMapOf<String, Any>()
        val preferences = preferences(values)
        val settings = PanelSettings(mediaIconColor = 0, ringerIconColor = 0xFF123456.toInt(),
            notificationIconColor = 0x80ABCDEF.toInt(), alarmIconColor = 0x00112233, hideMultiAppVolume = true,
            callIconColor = 0x80445566.toInt())
        PanelSettings.write(preferences, settings)
        assertEquals(settings, PanelSettings.fromValues(values))

        val reset = VolumeIconStream.NOTIFICATION.withColor(settings, null)
        PanelSettings.write(preferences, reset)
        assertFalse(values.containsKey("notification_icon_color"))
        assertEquals(reset, PanelSettings.fromValues(values))
        assertEquals(0, PanelSettings.fromValues(values).mediaIconColor)
        assertEquals(settings.ringerIconColor, PanelSettings.fromValues(values).ringerIconColor)
        assertEquals(settings.alarmIconColor, PanelSettings.fromValues(values).alarmIconColor)
        assertEquals(settings.callIconColor, PanelSettings.fromValues(values).callIconColor)
        PanelSettings.write(preferences, reset.copy(hideMultiAppVolume = false))
        assertFalse(PanelSettings.fromValues(values).hideMultiAppVolume)
    }

    @Test fun handsetAndBluetoothCallUseTheSameIndependentColor() {
        val settings = PanelSettings(alarmIconColor = 0xFF123456.toInt())
        val colored = VolumeIconStream.CALL.withColor(settings, 0x80123456.toInt())
        assertEquals(VolumeIconStream.CALL, VolumeIconStream.fromStream(0))
        assertEquals(VolumeIconStream.CALL, VolumeIconStream.fromStream(6))
        assertEquals(colored.callIconColor, VolumeIconStream.fromStream(6)?.color(colored))
        assertEquals(settings.alarmIconColor, VolumeIconStream.fromStream(4)?.color(colored))
        assertNull(VolumeIconStream.fromStream(10001))
        val values = mutableMapOf<String, Any>()
        PanelSettings.write(preferences(values), colored)
        assertEquals(colored, PanelSettings.fromValues(values))
        val reset = VolumeIconStream.CALL.withColor(colored, null)
        PanelSettings.write(preferences(values), reset)
        assertFalse(values.containsKey("call_icon_color"))
        assertEquals(settings, PanelSettings.fromValues(values))
    }

    private fun preferences(values: MutableMap<String, Any>): SharedPreferences {
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putInt", "putFloat", "putBoolean" -> { values[args!![0] as String] = args[1]; editor }
                "remove" -> { values.remove(args!![0] as String); editor }
                "commit" -> true
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, _ ->
            when (method.name) {
                "edit" -> editor
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
