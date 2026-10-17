package dev.glass.soundbar

import java.io.File
import java.io.OutputStream

/** App-only durable evidence; independent of the hook provider and its asynchronous worker. */
internal class AppDiagnosticStore(private val directory: File) {
    private val events get() = File(directory, "soundbar-app-diagnostics.log")
    private val crash get() = File(directory, "soundbar-app-crash.log")

    @Synchronized fun event(text: String) {
        directory.mkdirs()
        if (events.length() > 128 * 1024) events.writeText("Earlier app diagnostics rotated.\n", Charsets.UTF_8)
        events.appendText(text.take(32 * 1024) + "\n", Charsets.UTF_8)
    }

    @Synchronized fun fatal(report: String) {
        directory.mkdirs()
        crash.writeText(report.take(256 * 1024), Charsets.UTF_8)
    }

    @Synchronized fun export(output: OutputStream) {
        output.write("\n=== Application diagnostics ===\n".toByteArray())
        if (events.isFile) events.inputStream().use { it.copyTo(output) }
        output.write("\n=== Last application crash ===\n".toByteArray())
        if (crash.isFile) crash.inputStream().use { it.copyTo(output) }
        else output.write("No application crash has been captured by this diagnostic build.\n".toByteArray())
    }
}
