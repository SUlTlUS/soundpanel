package dev.glass.systemuiswitcher

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class RootFailure(val output: String) : Exception(output.takeLast(1800))

class SwitchEngine(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("switch", Context.MODE_PRIVATE)
    private val helper = File(app.filesDir, "root-helper.sh")
    val lastMessage: String get() = prefs.getString("last", "") ?: ""
    val rootPreviouslyGranted: Boolean get() = prefs.getBoolean("root", false)
    var logs: String
        get() = prefs.getString("log", "") ?: ""
        private set(value) { prefs.edit().putString("log", value.takeLast(24000)).apply() }

    fun note(value: String) { logs = logs + "\n" + value }
    private fun message(value: String) { prefs.edit().putString("last", value).apply(); note(value) }
    fun pending(): Pending? {
        val kind = prefs.getString("pending-kind", null) ?: return null
        return Pending(kind, prefs.getString("pending-boot", "")!!,
            prefs.getString("pending-backup", "")!!, prefs.getString("pending-path", "")!!)
    }
    private fun savePending(p: Pending) {
        check(prefs.edit().putString("pending-kind", p.kind).putString("pending-boot", p.boot)
            .putString("pending-backup", p.backup).putString("pending-path", p.oldPath).commit())
    }
    private fun clearPending() {
        prefs.edit().remove("pending-kind").remove("pending-boot").remove("pending-backup").remove("pending-path").commit()
    }

    private fun root(action: String, vararg args: String): String {
        app.assets.open("root-helper.sh").use { input -> helper.outputStream().use { input.copyTo(it) } }
        val command = (listOf("sh", helper.absolutePath, action) + args).joinToString(" ", transform = Safety::quote)
        val process = try {
            ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        } catch (e: IOException) {
            note("无法启动 su：${e.message}")
            throw RootFailure("无法启动 root 命令，请确认设备已 Root，且 root 管理器允许本应用使用 su。")
        }
        val captured = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                synchronized(captured) { if (captured.length < 64000) captured.appendLine(line) }
            } }
        }.apply { isDaemon = true; start() }
        if (!process.waitFor(75, TimeUnit.SECONDS)) {
            process.destroy()
            throw RootFailure("root 操作超时。任务可能仍在进行，请先重新检测，不要重复安装。")
        }
        reader.join(1500)
        val output = synchronized(captured) { captured.toString() }
        note("[$action] $output")
        if (process.exitValue() != 0) throw RootFailure(output.ifBlank { "root 操作失败（${process.exitValue()}）。请确认 root 授权。" })
        return output
    }

    fun inspect(): Snapshot {
        val output = root("inspect")
        val fields = output.lineSequence().filter { it.contains('=') }.associate {
            val p = it.indexOf('='); it.substring(0, p) to it.substring(p + 1).trim()
        }
        check(fields["ROOT_UID"] == "0") { "尚未获得 root 权限" }
        prefs.edit().putBoolean("root", true).apply()
        val snapshot = Snapshot(fields["MODEL"].orEmpty(), fields["ROM"].orEmpty(),
            fields["SDK"]?.toIntOrNull() ?: 0, fields["BOOT"].orEmpty(),
            fields["CURRENT_PATH"].orEmpty(), fields["CURRENT_HASH"].orEmpty(),
            fields["SYSTEM_HASH"].orEmpty(), Safety.readySessions(output), fields["RUNNING_MATCH"] == "true", Safety.pendingSessions(output))
        check(snapshot.boot.isNotBlank()) { "无法读取启动标识，已停止操作" }
        pending()?.let { p ->
            if (snapshot.boot != p.boot) {
                val applied = if (p.kind == "restore") snapshot.currentPath == Safety.SYSTEM_APK && snapshot.variant == Variant.ACE6
                    else snapshot.variant == Variant.OP15 && Safety.currentAllowed(snapshot)
                if (applied && snapshot.runningMatches) { clearPending(); message("已核验生效：${snapshot.variant.label}") }
                else if (!applied && p.kind == "install" && snapshot.stagedSessions.isEmpty()) {
                    clearPending(); message("上次安装未生效，当前为${snapshot.variant.label}。请查看操作记录。")
                } else message("上次操作尚未核验成功，已保留记录和备份。")
            }
        }
        return snapshot
    }

    private fun validate(s: Snapshot) {
        check(Safety.supported(s)) { "不支持当前 ROM：需要 API 37，且系统自带 APK 必须与所提供的 Ace 6 原版完全一致。" }
        check(Safety.currentAllowed(s)) { "当前 APK 或安装路径不在支持范围内，未做任何切换。" }
    }
    private fun cancelSessions(s: Snapshot) {
        if (s.stagedSessions.isNotEmpty()) {
            root("cancel", s.stagedSessions.joinToString(","))
            check(inspect().stagedSessions.isEmpty()) { "旧 SystemUI 安装会话尚未撤销，已停止操作。请重新检测。" }
        }
    }

    fun installOnePlus15(progress: (Int) -> Unit): Snapshot {
        check(pending() == null) { "请先重启或撤销待生效操作" }
        val before = inspect(); validate(before)
        if (before.variant == Variant.OP15 && before.stagedSessions.isEmpty()) {
            message("已经是指定的一加 15 版"); return before
        }
        val apk = File(app.filesDir, "oneplus15.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        app.assets.open("apks/oneplus15.apk").use { input ->
            apk.outputStream().use { output ->
                val buffer = ByteArray(65536); var total = 0L; var percent = -1
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    output.write(buffer, 0, count); digest.update(buffer, 0, count); total += count
                    val next = (total * 100 / 98817120L).toInt().coerceAtMost(100)
                    if (next != percent) { progress(next); percent = next }
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        check(hash == Safety.OP15_HASH) { "内置 APK 校验失败，已停止安装" }
        val fresh = inspect(); validate(fresh)
        check(fresh.boot == before.boot) { "手机启动状态已变化，请重新检测" }
        cancelSessions(fresh)
        progress(-1)
        savePending(Pending("install", before.boot))
        try {
            root("install", apk.absolutePath)
            val after = inspect()
            check(after.sessions.isNotEmpty()) { "没有检测到校验通过的安装会话，请查看操作记录。" }
            message("一加 15 版已提交，重启后生效")
            return after
        } catch (e: Exception) {
            runCatching { inspect() }.getOrNull()?.let { if (it.stagedSessions.isEmpty()) clearPending() }
            throw e
        }
    }

    fun restoreAce6(): Snapshot {
        check(pending() == null) { "请先重启或撤销待生效操作" }
        val before = inspect(); validate(before); cancelSessions(before)
        if (before.variant == Variant.ACE6) {
            message("当前已是指定的 Ace 6 原版，已取消待生效的 SystemUI 安装")
            return inspect()
        }
        val backup = "/data/local/tmp/systemui-switcher-backups/restore-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        val p = Pending("restore", before.boot, backup, before.currentPath.removeSuffix("/base.apk"))
        savePending(p)
        try {
            root("restore", backup, before.currentPath)
            message("一加 15 更新已备份并移出，重启后加载 Ace 6 原版\n备份：$backup")
            return inspect()
        } catch (e: Exception) {
            runCatching { inspect() }.getOrNull()?.let { if (it.currentHash == Safety.OP15_HASH) clearPending() }
            throw e
        }
    }

    fun cancelPending(): Snapshot {
        val before = inspect()
        check(Safety.supported(before)) { "系统原版校验不一致，已停止撤销" }
        val p = pending()
        if (p?.kind == "restore") {
            check(before.boot == p.boot) { "已经重启，不能直接放回旧更新。请重新检测恢复结果。" }
            root("undo-restore", p.backup, p.oldPath, p.boot)
        }
        cancelSessions(before)
        clearPending(); message("已撤销待生效操作")
        return inspect()
    }

    fun reboot() {
        val before = inspect()
        val p = pending() ?: error("没有待生效操作")
        check(Safety.supported(before) && before.boot == p.boot) { "状态已变化，请重新检测" }
        if (p.kind == "install") check(before.sessions.isNotEmpty()) { "安装会话尚未就绪" }
        else check(before.currentHash.isEmpty()) { "更新尚未移出，已停止重启" }
        message("已请求重启，重新打开应用后会核验生效结果")
        root("reboot")
    }
}
