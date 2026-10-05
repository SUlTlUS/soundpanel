package dev.glass.soundbar

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

internal data class PanelSettings(val enabled: Boolean = true, val size: Float = 1f, val tint: Float = 0.22f) {
    companion object {
        val uri: Uri = Uri.parse("content://dev.glass.soundbar.settings/config")
        fun read(context: Context): PanelSettings = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) PanelSettings(c.getInt(0) != 0, c.getFloat(1), c.getFloat(2)) else PanelSettings()
            } ?: PanelSettings()
        }.getOrDefault(PanelSettings())
    }
}

internal object ActivationStatus : XposedServiceHelper.OnServiceListener {
    @Volatile private var service: XposedService? = null

    fun connectToManager() = XposedServiceHelper.registerListener(this)

    override fun onServiceBind(service: XposedService) {
        this.service = service
        Log.i("GlassSoundbar", "LSPosed manager connected; SystemUI in scope=${service.scope.contains("com.android.systemui")}")
    }

    override fun onServiceDied(service: XposedService) {
        if (this.service === service) {
            this.service = null
            Log.w("GlassSoundbar", "LSPosed manager service disconnected")
        }
    }

    fun isEnabledForSystemUi(): Boolean? = service?.scope?.contains("com.android.systemui")

    fun requestScopeRestart() {
        Thread {
            runCatching {
                val root = ProcessBuilder("su").redirectErrorStream(true).start()
                root.outputStream.bufferedWriter().use { writer ->
                    writer.write("killall com.android.systemui\n")
                    writer.write("exit\n")
                    writer.flush()
                }
                val result = root.waitFor()
                android.util.Log.i("GlassSoundbar", "Root scope restart finished: exit=$result")
            }.onFailure {
                android.util.Log.e("GlassSoundbar", "Root scope restart failed", it)
            }
        }.start()
    }

}

class SoundbarApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        ActivationStatus.connectToManager()
    }
}

class SettingsProvider : ContentProvider() {
    private val debugLock = Any()
    private val debugFile: File get() = File(context!!.filesDir, "soundbar-debug.log")

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        args: Array<out String>?,
        sort: String?,
    ): Cursor {
        check(isOwnCaller() || isSystemUiCaller()) { "Only the module app or SystemUI can read settings" }
        return when (uri.lastPathSegment) {
            else -> {
                val p = context!!.getSharedPreferences("panel", Context.MODE_PRIVATE)
                MatrixCursor(arrayOf("enabled", "size", "tint")).apply {
                    addRow(arrayOf<Any>(if (p.getBoolean("enabled", true)) 1 else 0, p.getFloat("size", 1f), p.getFloat("tint", 0.22f)))
                }
            }
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
        return 0
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        check(isSystemUiCaller()) { "Only SystemUI can append module diagnostics" }
        if (method != "append_debug_log") return null
        val entry = extras?.getString("entry")?.takeLast(32 * 1024) ?: return null
        synchronized(debugLock) {
            if (debugFile.length() + entry.length * 3 > 512 * 1024) {
                val file = RandomAccessFile(debugFile, "rw")
                val keep = minOf(256 * 1024L, file.length())
                file.seek(file.length() - keep)
                val tail = ByteArray(keep.toInt())
                file.readFully(tail)
                val firstLine = tail.indexOf('\n'.code.toByte()) + 1
                file.setLength(0)
                file.write(tail, firstLine, tail.size - firstLine)
                file.close()
            }
            FileOutputStream(debugFile, true).bufferedWriter().use { it.append(entry) }
        }
        return Bundle()
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        check(mode == "r" && (isOwnCaller() || context!!.checkCallingUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            "Debug log is read-only and requires an app or URI read grant"
        }
        if (!debugFile.exists()) debugFile.writeText("No SystemUI diagnostic events have been recorded yet.\n")
        return ParcelFileDescriptor.open(debugFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun isOwnCaller(): Boolean = Binder.getCallingUid() == android.os.Process.myUid()

    private fun isSystemUiCaller(): Boolean {
        val uid = Binder.getCallingUid()
        return context!!.packageManager.getPackagesForUid(uid)?.contains("com.android.systemui") == true
    }

    override fun getType(uri: Uri) = if (uri.lastPathSegment == "debug-log") "text/plain" else "vnd.android.cursor.item/vnd.glass.config"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
}
