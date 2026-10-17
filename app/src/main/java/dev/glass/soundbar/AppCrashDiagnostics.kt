package dev.glass.soundbar

import android.content.Context
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

internal object AppCrashDiagnostics {
    const val revision = "games-auto-volume-owner-20261017"
    private val installed = AtomicBoolean(false)
    @Volatile private var lastStage = "app.start"
    @Volatile private var store: AppDiagnosticStore? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        store = AppDiagnosticStore(context.filesDir)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Write synchronously before Android terminates the process. Never swallow fatal errors.
            runCatching {
                val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                store?.fatal("$revision\nBuild: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                    "Time: ${System.currentTimeMillis()}\nThread: ${thread.name}\nLast stage: $lastStage\n" +
                    "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}; ${android.os.Build.DISPLAY}\n$stack")
            }
            if (previous != null) previous.uncaughtException(thread, error) else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
        record("app.start")
    }

    fun record(stage: String, error: Throwable? = null) {
        lastStage = stage
        runCatching {
            val entry = "${System.currentTimeMillis()} [$revision] $stage" +
                (error?.let { "\n${it.stackTraceToString()}" } ?: "")
            if (error == null) Log.i("SoundbarApp", entry) else Log.e("SoundbarApp", entry)
            store?.event(entry)
        }
    }
}
