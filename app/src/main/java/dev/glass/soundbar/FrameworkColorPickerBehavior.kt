package dev.glass.soundbar

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.saveable.listSaver

internal val AndroidPickerColorSaver = listSaver<Color?, Float>(
    save = { color -> color?.let { listOf(it.red, it.green, it.blue, it.alpha) } ?: emptyList() },
    restore = { channels ->
        if (channels.isEmpty()) null else Color(channels[0], channels[1], channels[2], channels[3])
    },
)

/** Behavior-only channel editing; the existing gradients, knobs and source colors stay intact. */
internal fun androidColorChannel(color: Color, channel: Int, value: Float): Color {
    val channels = floatArrayOf(color.red, color.green, color.blue)
    channels[channel] = value.coerceIn(0f, 1f)
    return Color(channels[0], channels[1], channels[2], 1f)
}
