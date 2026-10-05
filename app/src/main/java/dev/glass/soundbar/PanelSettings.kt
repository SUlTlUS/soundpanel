package dev.glass.soundbar

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

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

internal object ActivationStatus {
    val uri: Uri = Uri.parse("content://dev.glass.soundbar.settings/activation")
    private const val HEARTBEAT_INTERVAL_MS = 30_000L
    internal const val ACTIVE_TIMEOUT_MS = 75_000L
    @Volatile private var heartbeatStarted = false

    fun startHeartbeat(context: Context) {
        val appContext = context.applicationContext ?: context
        markActive(appContext)
        if (heartbeatStarted) return
        heartbeatStarted = true
        val handler = Handler(Looper.getMainLooper())
        val task = object : Runnable {
            override fun run() {
                markActive(appContext)
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
        handler.postDelayed(task, HEARTBEAT_INTERVAL_MS)
    }

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

    private fun markActive(context: Context) {
        runCatching {
            context.contentResolver.update(uri, ContentValues(), null, null)
        }.onSuccess {
            android.util.Log.i("GlassSoundbar", "LSPosed activation heartbeat updated")
        }.onFailure {
            android.util.Log.w("GlassSoundbar", "LSPosed activation heartbeat failed", it)
        }
    }

    fun isActive(context: Context): Boolean = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) != 0
        } ?: false
    }.getOrDefault(false)
}

class SettingsProvider : ContentProvider() {
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
            "activation" -> {
                val lastSeen = context!!.getSharedPreferences("activation", Context.MODE_PRIVATE)
                    .getLong("last_seen_elapsed", -1L)
                val now = SystemClock.elapsedRealtime()
                val active = lastSeen >= 0L && now >= lastSeen && now - lastSeen <= ActivationStatus.ACTIVE_TIMEOUT_MS
                MatrixCursor(arrayOf("active")).apply { addRow(arrayOf(if (active) 1 else 0)) }
            }
            else -> {
                val p = context!!.getSharedPreferences("panel", Context.MODE_PRIVATE)
                MatrixCursor(arrayOf("enabled", "size", "tint")).apply {
                    addRow(arrayOf<Any>(if (p.getBoolean("enabled", true)) 1 else 0, p.getFloat("size", 1f), p.getFloat("tint", 0.22f)))
                }
            }
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
        return when (uri.lastPathSegment) {
            "activation" -> {
                check(isSystemUiCaller()) { "Only SystemUI can report module activation" }
                context!!.getSharedPreferences("activation", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("last_seen_elapsed", SystemClock.elapsedRealtime())
                    .apply()
                1
            }
            else -> 0
        }
    }

    private fun isOwnCaller(): Boolean = Binder.getCallingUid() == android.os.Process.myUid()

    private fun isSystemUiCaller(): Boolean {
        val uid = Binder.getCallingUid()
        return context!!.packageManager.getPackagesForUid(uid)?.contains("com.android.systemui") == true
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.glass.config"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
}
