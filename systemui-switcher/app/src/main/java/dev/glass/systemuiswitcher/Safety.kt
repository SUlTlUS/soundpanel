package dev.glass.systemuiswitcher

enum class Variant(val label: String) { ACE6("Ace 6 原版"), OP15("一加 15 版"), UNKNOWN("未知版本") }

object Safety {
    const val ACE_HASH = "29ff19621cb37017cfd195109e2b4ed202175c72c1a0d247519ec866d1dabcbe"
    const val OP15_HASH = "31a3d5ad620488c64a77ac8a08b051686b2e177d84445c614cb904a129391a77"
    const val SYSTEM_APK = "/system_ext/priv-app/SystemUI/SystemUI.apk"
    const val PACKAGE = "com.android.systemui"
    private val updatePath = Regex("^/data/app/[A-Za-z0-9_~+=.-]+/com\\.android\\.systemui-[A-Za-z0-9_~+=.-]+/base\\.apk$")

    fun variant(hash: String) = when (hash.lowercase()) {
        ACE_HASH -> Variant.ACE6
        OP15_HASH -> Variant.OP15
        else -> Variant.UNKNOWN
    }
    fun supported(s: Snapshot) = s.sdk == 37 && s.systemHash == ACE_HASH
    fun updatePathAllowed(path: String) = !path.contains("/../") && !path.contains("/./") && updatePath.matches(path)
    fun currentAllowed(s: Snapshot) = when (variant(s.currentHash)) {
        Variant.ACE6 -> s.currentPath == SYSTEM_APK || updatePathAllowed(s.currentPath)
        Variant.OP15 -> updatePathAllowed(s.currentPath)
        Variant.UNKNOWN -> false
    }
    fun quote(value: String): String {
        require(!value.contains('\n') && !value.contains('\r') && !value.contains('\u0000'))
        return "'" + value.replace("'", "'\\''") + "'"
    }
    fun readySessions(text: String): List<Int> = sessions(text, true)
    fun pendingSessions(text: String): List<Int> = sessions(text, false)
    private fun sessions(text: String, readyOnly: Boolean): List<Int> = text
        .split(Regex("(?m)(?=^[ \\t]*sessionId[ \\t]*=)"))
        .asSequence().filter { it.trimStart().startsWith("sessionId") }.mapNotNull { record ->
        // ColorOS wraps fields mid-word. Join one whole session before parsing its fields.
        val flat = record.lineSequence().joinToString("") { it.trim() }
        val fields = flat.split(';').mapNotNull { field ->
            val p = field.indexOf('=')
            if (p < 0) null else field.substring(0, p).trim() to field.substring(p + 1).trim()
        }.toMap()
        if (fields["appPackageName"] == PACKAGE && fields["isStaged"] == "true" &&
            (!readyOnly || fields["isReady"] == "true") && fields["isApplied"] == "false" && fields["isFailed"] == "false")
            fields["sessionId"]?.toIntOrNull()?.takeIf { it > 0 } else null
    }.distinct().toList()
}

data class Snapshot(
    val model: String, val rom: String, val sdk: Int, val boot: String,
    val currentPath: String, val currentHash: String, val systemHash: String,
    val sessions: List<Int>, val runningMatches: Boolean = false, val stagedSessions: List<Int> = sessions
) {
    val variant get() = Safety.variant(currentHash)
}

data class Pending(val kind: String, val boot: String, val backup: String = "", val oldPath: String = "")
