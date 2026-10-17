package dev.glass.soundbar

/** Default entries follow the framework's light/dark palette; custom entries retain full ARGB. */
internal sealed interface PickerColorPreset {
    data class Default(val index: Int) : PickerColorPreset
    data class Custom(val argb: Int) : PickerColorPreset
}

internal object PickerColorPresets {
    private val defaults = List(9) { PickerColorPreset.Default(it) }

    fun decode(saved: String?): List<PickerColorPreset> {
        if (saved == null) return defaults
        if (saved.isEmpty()) return emptyList()
        val entries = saved.split(',').mapNotNull { token ->
            when {
                token.startsWith("d:") -> token.drop(2).toIntOrNull()?.takeIf { it in 0..8 }
                    ?.let(PickerColorPreset::Default)
                token.startsWith("c:") && token.length == 10 -> token.drop(2).toLongOrNull(16)
                    ?.takeIf { it in 0L..0xFFFFFFFFL }?.toInt()?.let(PickerColorPreset::Custom)
                else -> null
            }
        }.distinct()
        return entries.ifEmpty { defaults }
    }

    fun encode(entries: List<PickerColorPreset>): String = entries.joinToString(",") {
        when (it) {
            is PickerColorPreset.Default -> "d:${it.index}"
            is PickerColorPreset.Custom -> "c:%08X".format(it.argb)
        }
    }

    fun color(entry: PickerColorPreset, defaults: List<Int>): Int = when (entry) {
        is PickerColorPreset.Default -> defaults[entry.index]
        is PickerColorPreset.Custom -> entry.argb
    }

    fun add(entries: List<PickerColorPreset>, argb: Int, defaults: List<Int>): List<PickerColorPreset> =
        if (entries.any { color(it, defaults) == argb }) entries else entries + PickerColorPreset.Custom(argb)
}
