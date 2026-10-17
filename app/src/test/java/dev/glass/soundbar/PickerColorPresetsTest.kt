package dev.glass.soundbar

import org.junit.Assert.*
import org.junit.Test

class PickerColorPresetsTest {
    private val light = List(9) { 0xFF000000.toInt() + it }
    private val dark = List(9) { 0xFF100000.toInt() + it }

    @Test fun firstOpenUsesDefaultsButDeletingEverythingStaysEmptyAfterReload() {
        assertEquals(9, PickerColorPresets.decode(null).size)
        assertEquals(emptyList<PickerColorPreset>(), PickerColorPresets.decode(PickerColorPresets.encode(emptyList())))
    }

    @Test fun customColorsPreserveOrderAlphaAndSignAfterReload() {
        var entries = PickerColorPresets.decode(null)
        for (color in listOf(0, 0x80ABCDEF.toInt(), 0xFFFFFFFF.toInt())) {
            entries = PickerColorPresets.add(entries, color, light)
        }
        assertEquals(entries, PickerColorPresets.decode(PickerColorPresets.encode(entries)))
        assertEquals(listOf(0, 0x80ABCDEF.toInt(), -1), entries.takeLast(3).map { PickerColorPresets.color(it, light) })
    }

    @Test fun duplicateDefaultOrCustomColorDoesNotUseAnotherSlot() {
        val defaults = PickerColorPresets.decode(null)
        assertEquals(defaults, PickerColorPresets.add(defaults, light[3], light))
        val added = PickerColorPresets.add(defaults, 0x55667788, light)
        assertEquals(added, PickerColorPresets.add(added, 0x55667788, light))
    }

    @Test fun deletionPersistsAcrossThemesAndCustomColorsDoNotChange() {
        val original = PickerColorPresets.add(PickerColorPresets.decode(null), 0x00123456, light)
        val removed = original.filterNot { it == PickerColorPreset.Default(3) }
        val loaded = PickerColorPresets.decode(PickerColorPresets.encode(removed))
        assertEquals(removed, loaded)
        assertFalse(loaded.contains(PickerColorPreset.Default(3)))
        assertEquals(light[0], PickerColorPresets.color(loaded.first(), light))
        assertEquals(dark[0], PickerColorPresets.color(loaded.first(), dark))
        assertEquals(0x00123456, PickerColorPresets.color(loaded.last(), dark))
    }

    @Test fun corruptValuesAreRejectedAndValidEntriesRemainOrdered() {
        assertEquals(PickerColorPresets.decode(null), PickerColorPresets.decode("d:99,c:ZZZZZZZZ,unknown"))
        assertEquals(listOf(PickerColorPreset.Custom(-1), PickerColorPreset.Default(2)),
            PickerColorPresets.decode("c:FFFFFFFF,bad,d:2,d:2,c:100000000"))
    }
}
