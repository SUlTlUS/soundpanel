package dev.glass.soundbar

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

/** Mirrors useful SystemUI hook events into the app's private, shareable log. */
internal object ModuleDebugLog {
    private val worker = HandlerThread("SoundbarDiagnostics").apply { start() }.let { Handler(it.looper) }
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun i(tag: String, message: String) = write(Log.INFO, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = write(Log.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write(Log.ERROR, tag, message, error)

    private fun write(priority: Int, tag: String, message: String, error: Throwable?) {
        Log.println(priority, tag, message + (error?.let { "\n${it.stackTraceToString()}" } ?: ""))
        val context = appContext ?: return
        val entry = buildString {
            append(System.currentTimeMillis())
            append(" ")
            append(when (priority) {
                Log.ERROR -> "ERROR"
                Log.WARN -> "WARN"
                else -> "INFO"
            })
            append(" ")
            append(tag)
            append(": ")
            append(" ")
            append(message)
            if (error != null) {
                append('\n')
                append(StringWriter().also { error.printStackTrace(PrintWriter(it)) })
            }
            append('\n')
        }
        worker.post {
            runCatching {
                context.contentResolver.call(PanelSettings.uri, "append_debug_log", null, Bundle().apply {
                    putString("entry", entry)
                })
            }
        }
    }
}
