package dev.glass.soundbar

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

/** Mirrors useful system hook events into the app's private, shareable log. */
internal object ModuleDebugLog {
    private val worker by lazy { HandlerThread("SoundbarDiagnostics").apply { start() }.let { Handler(it.looper) } }
    @Volatile private var appContext: Context? = null
    @Volatile private var minimumPriority = Log.INFO
    private var preferences: SharedPreferences? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { source, key ->
        if (key == null || key == "hook_log_level") setLevel(source.getInt("hook_log_level", Log.INFO))
    }
    private val runtimeTag = "[module=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) pid=${android.os.Process.myPid()} session=${System.currentTimeMillis()}]"

    @Synchronized
    fun initialize(context: Context) {
        val first = appContext == null
        appContext = context.applicationContext ?: context
        if (first && preferences == null) setLevel(PanelSettings.read(context).hookLogLevel)
        if (first) i("GlassSoundbar", "Module logging started: process=${context.packageName}; ${DeviceProfile.detectionSummary}")
    }

    fun bind(source: SharedPreferences) {
        preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        preferences = source
        setLevel(source.getInt("hook_log_level", Log.INFO))
        source.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun setLevel(priority: Int) { minimumPriority = HookLogLevel.fromPriority(priority).priority }
    fun accepts(priority: Int): Boolean = priority >= minimumPriority

    fun v(tag: String, message: String) = write(Log.VERBOSE, tag, message, null)
    fun d(tag: String, message: String) = write(Log.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = write(Log.INFO, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = write(Log.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write(Log.ERROR, tag, message, error)

    private fun write(priority: Int, tag: String, message: String, error: Throwable?) {
        if (!accepts(priority)) return
        Log.println(priority, tag, "$runtimeTag $message" + (error?.let { "\n${it.stackTraceToString()}" } ?: ""))
        val context = appContext ?: return
        val entry = buildString {
            append(System.currentTimeMillis())
            append(" ")
            append(runtimeTag)
            append(" ")
            append(when (priority) {
                Log.ERROR -> "ERROR"
                Log.WARN -> "WARN"
                Log.DEBUG -> "DEBUG"
                Log.VERBOSE -> "VERBOSE"
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
            if (!accepts(priority)) return@post
            runCatching {
                context.contentResolver.call(PanelSettings.uri, "append_debug_log", null, Bundle().apply {
                    putString("entry", entry)
                })
            }
        }
    }
}
