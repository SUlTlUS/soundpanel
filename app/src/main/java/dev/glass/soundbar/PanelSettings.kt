package dev.glass.soundbar

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

internal data class PanelSettings(
    val enabled: Boolean = true,
    val size: Float = 1f,
    val tint: Float = 0.22f,
    val tone: Float = 0f,
    val ringerButton: Boolean = true,
    val dndButton: Boolean = true,
    val headsetButton: Boolean = true,
    val hideLabels: Boolean = false,
    val radiusScale: Float = 1f,
    val darkTone: Float = tone,
) {
    fun toneFor(context: Context): Float =
        if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
            darkTone
        } else tone

    companion object {
        val uri: Uri by lazy { Uri.parse("content://dev.glass.soundbar.settings/config") }
        private var remote: SharedPreferences? = null
        private var lastRead: PanelSettings? = null

        fun connectRemote(preferences: SharedPreferences) { remote = preferences }

        fun read(context: Context): PanelSettings {
            runCatching { remote?.all?.takeIf { it["configured"] == true }?.let(::fromValues) }
                .onFailure { ModuleDebugLog.e("GlassSoundbar", "LSPosed settings read failed", it) }
                .getOrNull()?.let { return remember(context, it) }
            val providerValue = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) PanelSettings(
                        c.getInt(0) != 0, c.getFloat(1), c.getFloat(2), c.getFloat(3),
                        c.getInt(4) != 0, c.getInt(5) != 0, c.getInt(6) != 0,
                        c.getColumnIndex("hide_labels").let { it >= 0 && c.getInt(it) != 0 },
                        c.getColumnIndex("radius_scale").let { if (it >= 0) c.getFloat(it).coerceIn(0f, 1f) else 1f },
                        c.getColumnIndex("dark_tone").let { if (it >= 0) c.getFloat(it).coerceIn(-1f, 1f) else c.getFloat(3) },
                    ) else null
                }
            }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Panel settings provider unavailable; retaining saved settings", it) }.getOrNull()
            if (providerValue != null) return remember(context, providerValue)
            return lastRead ?: fromValues(context.getSharedPreferences("soundbar_config_snapshot", Context.MODE_PRIVATE).all)
                .also { lastRead = it }
        }

        private fun remember(context: Context, value: PanelSettings): PanelSettings {
            if (lastRead != value) {
                write(context.getSharedPreferences("soundbar_config_snapshot", Context.MODE_PRIVATE), value)
                lastRead = value
            }
            return value
        }

        fun fromValues(p: Map<String, *>): PanelSettings = PanelSettings(
            p["enabled"] as? Boolean ?: true, p["size"] as? Float ?: 1f,
            p["tint"] as? Float ?: 0.22f, (p["tone"] as? Float ?: 0f).coerceIn(-1f, 1f),
            p["ringer_button"] as? Boolean ?: true, p["dnd_button"] as? Boolean ?: true,
            p["headset_button"] as? Boolean ?: true, p["hide_labels"] as? Boolean ?: false,
            (p["radius_scale"] as? Float ?: 1f).coerceIn(0f, 1f),
            (p["dark_tone"] as? Float ?: p["tone"] as? Float ?: 0f).coerceIn(-1f, 1f),
        )

        fun local(context: Context): PanelSettings {
            return fromValues(context.getSharedPreferences("panel", Context.MODE_PRIVATE).all)
        }

        fun save(context: Context, settings: PanelSettings) {
            write(context.getSharedPreferences("panel", Context.MODE_PRIVATE), settings)
            ActivationStatus.publish(settings)
            context.contentResolver.notifyChange(uri, null)
        }

        fun write(preferences: SharedPreferences, settings: PanelSettings) {
            check(preferences.edit()
                .putBoolean("configured", true)
                .putBoolean("enabled", settings.enabled)
                .putFloat("size", settings.size)
                .putFloat("tint", settings.tint)
                .putFloat("tone", settings.tone.coerceIn(-1f, 1f))
                .putFloat("dark_tone", settings.darkTone.coerceIn(-1f, 1f))
                .putBoolean("ringer_button", settings.ringerButton)
                .putBoolean("dnd_button", settings.dndButton)
                .putBoolean("headset_button", settings.headsetButton)
                .putBoolean("hide_labels", settings.hideLabels)
                .putFloat("radius_scale", settings.radiusScale.coerceIn(0f, 1f))
                .commit()) { "Unable to persist panel settings" }
        }
    }
}

internal object ActivationStatus : XposedServiceHelper.OnServiceListener {
    @Volatile private var service: XposedService? = null
    private lateinit var appContext: Context

    fun connectToManager(context: Context) {
        appContext = context.applicationContext
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        this.service = service
        publish(PanelSettings.local(appContext))
        Log.i("GlassSoundbar", "LSPosed manager connected; SystemUI in scope=${service.scope.contains("com.android.systemui")}")
    }

    override fun onServiceDied(service: XposedService) {
        if (this.service === service) {
            this.service = null
            Log.w("GlassSoundbar", "LSPosed manager service disconnected")
        }
    }

    fun isEnabledForSystemUi(): Boolean? = service?.scope?.contains("com.android.systemui")

    fun publish(settings: PanelSettings) {
        val manager = service ?: return
        runCatching { PanelSettings.write(manager.getRemotePreferences("panel"), settings) }
            .onFailure { Log.e("GlassSoundbar", "LSPosed settings publish failed", it) }
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

}

class SoundbarApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        ActivationStatus.connectToManager(this)
    }
}

class SettingsProvider : ContentProvider() {
    private val debugLock = Any()
    private val debugFile: File get() = File(context!!.filesDir, "soundbar-debug.log")
    private val exportHeader: ByteArray
        get() = ("Soundbar diagnostics\n" +
            "Exporting app build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "Each [module=...] tag identifies the code loaded in the logging process.\n" +
            "The exporting app version does not prove SystemUI loaded that version.\n" +
            "Untagged entries are legacy records; their module version is unknown.\n\n").toByteArray(Charsets.UTF_8)

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        args: Array<out String>?,
        sort: String?,
    ): Cursor {
        if (uri.lastPathSegment == "debug-log") {
            check(isOwnCaller() || hasReadGrant(uri)) { "Debug log metadata requires a URI read grant" }
            if (!debugFile.exists()) debugFile.writeText("No SystemUI diagnostic events have been recorded yet.\n")
            val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            return MatrixCursor(columns).apply {
                val row = Array<Any?>(columns.size) { index ->
                    when (columns[index]) {
                        OpenableColumns.DISPLAY_NAME -> "soundbar-debug.log"
                        OpenableColumns.SIZE -> debugFile.length() + exportHeader.size
                        else -> null
                    }
                }
                addRow(row)
            }
        }
        check(isOwnCaller() || isSystemUiCaller()) { "Only the module app or SystemUI can read settings" }
        val settings = PanelSettings.local(context!!)
        return MatrixCursor(arrayOf("enabled", "size", "tint", "tone", "ringer_button", "dnd_button", "headset_button", "hide_labels", "radius_scale", "dark_tone")).apply {
            addRow(arrayOf<Any>(
                if (settings.enabled) 1 else 0, settings.size, settings.tint, settings.tone,
                if (settings.ringerButton) 1 else 0, if (settings.dndButton) 1 else 0,
                if (settings.headsetButton) 1 else 0,
                if (settings.hideLabels) 1 else 0,
                settings.radiusScale,
                settings.darkTone,
            ))
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
        check(mode == "r" && (isOwnCaller() || hasReadGrant(uri))) {
            "Debug log is read-only and requires an app or URI read grant"
        }
        // Export a snapshot so concurrent appends cannot mix the header and history.
        val snapshot = File.createTempFile("soundbar-export-", ".log", context!!.cacheDir)
        try {
            synchronized(debugLock) {
                snapshot.outputStream().use { output ->
                    output.write(exportHeader)
                    if (debugFile.exists()) debugFile.inputStream().use { it.copyTo(output) }
                    else output.write("No SystemUI diagnostic events have been recorded yet.\n".toByteArray())
                }
            }
            return ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally {
            // Android keeps the open descriptor readable after unlinking the file.
            snapshot.delete()
        }
    }

    private fun hasReadGrant(uri: Uri): Boolean =
        context!!.checkCallingUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun isOwnCaller(): Boolean = Binder.getCallingUid() == android.os.Process.myUid()

    private fun isSystemUiCaller(): Boolean {
        val uid = Binder.getCallingUid()
        return context!!.packageManager.getPackagesForUid(uid)?.contains("com.android.systemui") == true
    }

    override fun getType(uri: Uri) = if (uri.lastPathSegment == "debug-log") "text/plain" else "vnd.android.cursor.item/vnd.glass.config"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
}
