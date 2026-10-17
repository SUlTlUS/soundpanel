package dev.glass.soundbar

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import java.util.concurrent.atomic.AtomicBoolean

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
    val headsetModes: Int = 14,
    val autoThin: Boolean = true,
    val leftSide: Boolean = false,
    val blockAppVolume: Boolean = false,
    val portraitPosition: Float = 0f,
    val landscapePosition: Float = 0f,
    val hookLogLevel: Int = Log.INFO,
    val borderHighlight: Boolean = true,
    val lengthScale: Float = 1f,
    val mediaIconColor: Int? = null,
    val ringerIconColor: Int? = null,
    val alarmIconColor: Int? = null,
    val notificationIconColor: Int? = null,
    val hideMultiAppVolume: Boolean = false,
) {
    fun toneFor(context: Context): Float =
        if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
            darkTone
        } else tone

    fun positionFor(context: Context): Float =
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            landscapePosition
        } else portraitPosition

    companion object {
        val uri: Uri by lazy { Uri.parse("content://dev.glass.soundbar.settings/config") }
        private var remote: SharedPreferences? = null
        private var lastRead: PanelSettings? = null

        fun connectRemote(preferences: SharedPreferences) {
            remote = preferences
            ModuleDebugLog.bind(preferences)
        }

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
                        c.getColumnIndex("headset_modes").let { if (it >= 0) c.getInt(it) and 15 else 14 },
                        c.getColumnIndex("auto_thin").let { it < 0 || c.getInt(it) != 0 },
                        c.getColumnIndex("left_side").let { it >= 0 && c.getInt(it) != 0 },
                        c.getColumnIndex("block_app_volume").let { it >= 0 && c.getInt(it) != 0 },
                        c.getColumnIndex("portrait_position").let { if (it >= 0) c.getFloat(it).coerceIn(-1f, 1f) else 0f },
                        c.getColumnIndex("landscape_position").let { if (it >= 0) c.getFloat(it).coerceIn(-1f, 1f) else 0f },
                        c.getColumnIndex("hook_log_level").let { if (it >= 0) c.getInt(it) else Log.INFO },
                        c.getColumnIndex("border_highlight").let { it < 0 || c.getInt(it) != 0 },
                        c.getColumnIndex("length_scale").let { if (it >= 0) c.getFloat(it).coerceIn(0.8f, 1.2f) else 1f },
                        c.getColumnIndex("media_icon_color").let { if (it >= 0 && !c.isNull(it)) c.getInt(it) else null },
                        c.getColumnIndex("ringer_icon_color").let { if (it >= 0 && !c.isNull(it)) c.getInt(it) else null },
                        c.getColumnIndex("alarm_icon_color").let { if (it >= 0 && !c.isNull(it)) c.getInt(it) else null },
                        c.getColumnIndex("notification_icon_color").let { if (it >= 0 && !c.isNull(it)) c.getInt(it) else null },
                        c.getColumnIndex("hide_multi_app_volume").let { it >= 0 && c.getInt(it) != 0 },
                    ) else null
                }
            }.onFailure { ModuleDebugLog.e("GlassSoundbar", "Panel settings provider unavailable; retaining saved settings", it) }.getOrNull()
            if (providerValue != null) return remember(context, providerValue)
            return lastRead ?: fromValues(context.getSharedPreferences("soundbar_config_snapshot", Context.MODE_PRIVATE).all)
                .also { lastRead = it; ModuleDebugLog.setLevel(it.hookLogLevel) }
        }

        private fun remember(context: Context, value: PanelSettings): PanelSettings {
            ModuleDebugLog.setLevel(value.hookLogLevel)
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
            (p["headset_modes"] as? Int ?: 14) and 15,
            p["auto_thin"] as? Boolean ?: true,
            p["left_side"] as? Boolean ?: false,
            p["block_app_volume"] as? Boolean ?: false,
            (p["portrait_position"] as? Float ?: 0f).coerceIn(-1f, 1f),
            (p["landscape_position"] as? Float ?: 0f).coerceIn(-1f, 1f),
            HookLogLevel.fromPriority(p["hook_log_level"] as? Int ?: Log.INFO).priority,
            p["border_highlight"] as? Boolean ?: true,
            (p["length_scale"] as? Float ?: 1f).coerceIn(0.8f, 1.2f),
            p["media_icon_color"] as? Int,
            p["ringer_icon_color"] as? Int,
            p["alarm_icon_color"] as? Int,
            p["notification_icon_color"] as? Int,
            p["hide_multi_app_volume"] as? Boolean ?: false,
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
                .putInt("headset_modes", settings.headsetModes and 15)
                .putBoolean("auto_thin", settings.autoThin)
                .putBoolean("left_side", settings.leftSide)
                .putBoolean("block_app_volume", settings.blockAppVolume)
                .putBoolean("hide_multi_app_volume", settings.hideMultiAppVolume)
                .putFloat("portrait_position", settings.portraitPosition.coerceIn(-1f, 1f))
                .putFloat("landscape_position", settings.landscapePosition.coerceIn(-1f, 1f))
                .putInt("hook_log_level", settings.hookLogLevel)
                .putBoolean("border_highlight", settings.borderHighlight)
                .putFloat("length_scale", settings.lengthScale.coerceIn(0.8f, 1.2f))
                .putBoolean("ringer_button", settings.ringerButton)
                .putBoolean("dnd_button", settings.dndButton)
                .putBoolean("headset_button", settings.headsetButton)
                .putBoolean("hide_labels", settings.hideLabels)
                .putFloat("radius_scale", settings.radiusScale.coerceIn(0f, 1f))
                .apply {
                    VolumeIconStream.entries.forEach { stream ->
                        val color = stream.color(settings)
                        if (color == null) remove(stream.key) else putInt(stream.key, color)
                    }
                }
                .commit()) { "Unable to persist panel settings" }
        }
    }
}

internal object ActivationStatus : XposedServiceHelper.OnServiceListener {
    @Volatile private var service: XposedService? = null
    private val scopeRequestPending = AtomicBoolean(false)
    private val requiredScopes = listOf("com.android.systemui", "system")
    private lateinit var appContext: Context

    fun connectToManager(context: Context) {
        appContext = context.applicationContext
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        this.service = service
        publish(PanelSettings.local(appContext))
        Log.i("GlassSoundbar", "LSPosed manager connected; scopes=${scopeSummary()}")
    }

    override fun onServiceDied(service: XposedService) {
        if (this.service === service) {
            this.service = null
            scopeRequestPending.set(false)
            Log.w("GlassSoundbar", "LSPosed manager service disconnected")
        }
    }

    fun isReady(): Boolean = runCatching { service?.scope?.containsAll(requiredScopes) == true }
        .onFailure { Log.w("GlassSoundbar", "LSPosed activation query failed", it) }
        .getOrDefault(false)

    fun requestScope(context: Context, onResult: (String?) -> Unit) {
        requestScopes(context, requiredScopes, onResult)
    }

    fun hasGameScope(): Boolean = runCatching {
        service?.scope?.contains(GameMultiAppVolume.packageName) == true
    }.getOrDefault(false)

    fun requestGameScope(context: Context, onResult: (String?) -> Unit) {
        requestScopes(context, requiredScopes + GameMultiAppVolume.packageName, onResult)
    }

    private fun requestScopes(context: Context, scopes: List<String>, onResult: (String?) -> Unit) {
        val manager = service
        if (manager == null) {
            openManager(context, onResult)
            return
        }
        if (!scopeRequestPending.compareAndSet(false, true)) return
        runCatching {
            manager.requestScope(scopes, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(scope: List<String>) {
                    scopeRequestPending.set(false)
                    if (manager.scope.containsAll(scopes)) onResult(null) else {
                        onResult("请在 LSPosed 管理器中确认所需作用域；隐藏游戏音量条还需勾选游戏助手")
                        openManager(context, onResult)
                    }
                }
                override fun onScopeRequestFailed(message: String) {
                    scopeRequestPending.set(false)
                    onResult("作用域请求失败：$message")
                }
            })
        }.onFailure {
            scopeRequestPending.set(false)
            Log.e("GlassSoundbar", "LSPosed scope request failed", it)
            onResult("作用域请求失败")
        }
    }

    private fun openManager(context: Context, onResult: (String?) -> Unit) {
        runCatching {
            val packages = context.packageManager
            val intent = packages.getLaunchIntentForPackage("org.lsposed.manager")
                ?: packages.getLaunchIntentForPackage("com.android.shell")
                ?: packages.getPackageInfo(
                    "com.android.shell",
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()),
                ).activities?.firstOrNull { it.exported && it.enabled && it.processName == it.packageName }
                    ?.let { Intent(Intent.ACTION_MAIN).setClassName(it.packageName, it.name) }
                ?: error("LSPosed manager entry unavailable")
            intent.categories?.toList()?.forEach(intent::removeCategory)
            intent.addCategory("org.lsposed.manager.LAUNCH_MANAGER")
            intent.data = Uri.parse("module://${context.packageName}:${android.os.Process.myUid() / 100000}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            onResult(null)
        }.onFailure {
            Log.e("GlassSoundbar", "Unable to open LSPosed module scope", it)
            onResult("无法打开 LSPosed，请在管理器中启用模块并确认作用域")
        }
    }

    fun scopeSummary(): String = service?.scope?.sorted()?.joinToString() ?: "manager unavailable"

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
                    if (hasGameScope() && PanelSettings.local(appContext).hideMultiAppVolume) {
                        writer.write("killall com.oplus.games\n")
                    }
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
        AppCrashDiagnostics.install(this)
        ActivationStatus.connectToManager(this)
    }
}

private const val DEBUG_EXPORT_SNAPSHOT_PREFIX = "soundbar-debug-export-"
private const val DEBUG_EXPORT_SNAPSHOT_SUFFIX = ".log"
private const val DEBUG_EXPORT_SNAPSHOT_MAX_AGE_MS = 24L * 60 * 60 * 1000

class SettingsProvider : ContentProvider() {
    private val debugLock = Any()
    private val debugFile: File get() = File(context!!.filesDir, "soundbar-debug.log")
    private val exportHeader: ByteArray
        get() = ("Soundbar diagnostics\n" +
            "Exporting app build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
            "System: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}); ${android.os.Build.DISPLAY}\n" +
            "Configured hook log level: ${HookLogLevel.fromPriority(PanelSettings.local(context!!).hookLogLevel).title}\n" +
            "LSPosed configured scopes: ${ActivationStatus.scopeSummary()}\n" +
            "Each [module=...] tag identifies the code loaded in the logging process.\n" +
            "The exporting app version does not prove the hooked processes loaded that version.\n" +
            "Untagged entries are legacy records; their module version is unknown.\n\n").toByteArray(Charsets.UTF_8)

    override fun onCreate(): Boolean {
        runCatching { cleanupStaleExportSnapshots(context!!.cacheDir) }
        return true
    }

    private fun cleanupStaleExportSnapshots(cacheDir: File) {
        val cutoff = System.currentTimeMillis() - DEBUG_EXPORT_SNAPSHOT_MAX_AGE_MS
        cacheDir.listFiles { file ->
            file.isFile &&
                file.name.startsWith(DEBUG_EXPORT_SNAPSHOT_PREFIX) &&
                file.name.endsWith(DEBUG_EXPORT_SNAPSHOT_SUFFIX) &&
                file.lastModified() < cutoff
        }?.forEach { runCatching { it.delete() } }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        args: Array<out String>?,
        sort: String?,
    ): Cursor {
        if (uri.lastPathSegment == "debug-log") {
            check(isOwnCaller() || hasReadGrant(uri)) { "Debug log metadata requires a URI read grant" }
            if (!debugFile.exists()) debugFile.writeText("No hook diagnostic events have been recorded yet.\n")
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
        check(isOwnCaller() || isHookProcessCaller()) { "Only the module app or system hook processes can read settings" }
        val settings = PanelSettings.local(context!!)
        return MatrixCursor(arrayOf("enabled", "size", "tint", "tone", "ringer_button", "dnd_button", "headset_button", "hide_labels", "radius_scale", "dark_tone", "headset_modes", "auto_thin", "left_side", "block_app_volume", "portrait_position", "landscape_position", "hook_log_level", "border_highlight", "length_scale") + VolumeIconStream.entries.map { it.key } + "hide_multi_app_volume").apply {
            addRow(arrayOf<Any?>(
                if (settings.enabled) 1 else 0, settings.size, settings.tint, settings.tone,
                if (settings.ringerButton) 1 else 0, if (settings.dndButton) 1 else 0,
                if (settings.headsetButton) 1 else 0,
                if (settings.hideLabels) 1 else 0,
                settings.radiusScale,
                settings.darkTone,
                settings.headsetModes,
                if (settings.autoThin) 1 else 0,
                if (settings.leftSide) 1 else 0,
                if (settings.blockAppVolume) 1 else 0,
                settings.portraitPosition,
                settings.landscapePosition,
                settings.hookLogLevel,
                if (settings.borderHighlight) 1 else 0,
                settings.lengthScale,
                settings.mediaIconColor, settings.ringerIconColor, settings.notificationIconColor, settings.alarmIconColor,
                if (settings.hideMultiAppVolume) 1 else 0,
            ))
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
        return 0
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        check(isHookProcessCaller()) { "Only system hook processes can append module diagnostics" }
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
        val snapshot = File.createTempFile(
            DEBUG_EXPORT_SNAPSHOT_PREFIX,
            DEBUG_EXPORT_SNAPSHOT_SUFFIX,
            context!!.cacheDir,
        )
        try {
            synchronized(debugLock) {
                snapshot.outputStream().use { output ->
                    output.write(exportHeader)
                    if (debugFile.exists()) debugFile.inputStream().use { it.copyTo(output) }
                    else output.write("No hook diagnostic events have been recorded yet.\n".toByteArray())
                    AppDiagnosticStore(context!!.filesDir).export(output)
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

    private fun isHookProcessCaller(): Boolean {
        val uid = Binder.getCallingUid()
        return uid == android.os.Process.SYSTEM_UID ||
            context!!.packageManager.getPackagesForUid(uid)?.any {
                it == "com.android.systemui" || it == GameMultiAppVolume.packageName
            } == true
    }

    override fun getType(uri: Uri) = if (uri.lastPathSegment == "debug-log") "text/plain" else "vnd.android.cursor.item/vnd.glass.config"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
}
