package dev.glass.systemuiswitcher

import org.junit.Assert.*
import org.junit.Test

class SafetyTest {
    private val update = "/data/app/~~Ab_123==/com.android.systemui-New_456==/base.apk"
    private fun snapshot(hash: String = Safety.ACE_HASH, path: String = Safety.SYSTEM_APK,
                         stock: String = Safety.ACE_HASH, sdk: Int = 37) =
        Snapshot("arbitrary-model", "arbitrary-ROM", sdk, "boot-1", path, hash, stock, emptyList())

    @Test fun matchingRomDoesNotRequireSpecificModelOrDeviceIdentifier() {
        assertTrue(Safety.supported(snapshot()))
        assertTrue(Safety.currentAllowed(snapshot()))
    }
    @Test fun differentStockOrSdkIsRejectedEvenWithKnownActiveApk() {
        assertFalse(Safety.supported(snapshot(stock = Safety.OP15_HASH)))
        assertFalse(Safety.supported(snapshot(stock = "0".repeat(64))))
        assertFalse(Safety.supported(snapshot(sdk = 36)))
        assertFalse(Safety.supported(snapshot(sdk = 38)))
    }
    @Test fun unknownApkOrUnexpectedPathCannotBeMoved() {
        assertFalse(Safety.currentAllowed(snapshot(hash = "0".repeat(64), path = update)))
        assertFalse(Safety.currentAllowed(snapshot(hash = Safety.OP15_HASH)))
        assertFalse(Safety.currentAllowed(snapshot(hash = Safety.OP15_HASH, path = "/data/user/0/com.android.systemui/base.apk")))
        assertTrue(Safety.currentAllowed(snapshot(hash = Safety.OP15_HASH, path = update)))
    }
    @Test fun pathTraversalAndOtherPackagesAreRejected() {
        for (path in listOf("/data/app/../com.android.systemui-a/base.apk",
                "/data/app/~~abc/com.other.app-a/base.apk", "$update; reboot",
                "/data/app/~~abc/com.android.systemui-a/../base.apk", "$update\n")) {
            assertFalse(path, Safety.updatePathAllowed(path))
        }
        assertTrue(Safety.updatePathAllowed(update))
    }
    @Test fun onlyPendingSystemUiSessionsCanBeCancelledAndReadinessIsSeparate() {
        fun session(id: String, pkg: String = Safety.PACKAGE, ready: Boolean = true,
                    applied: Boolean = false, failed: Boolean = false) =
            "sessionId = $id; appPackageName = $pkg; isStaged = true; isReady = $ready; isApplied = $applied; isFailed = $failed; errorMsg = ;"
        val output = listOf(session("42"), session("42"), session("43", pkg = "com.other.app"),
            session("44", ready = false), session("45", applied = true), session("46", failed = true),
            session("-1"), session("hello"), session("47")).joinToString("\n")
        assertEquals(listOf(42, 47), Safety.readySessions(output))
        assertEquals(listOf(42, 44, 47), Safety.pendingSessions(output))
    }
    @Test fun realWrappedDeviceOutputFindsTheBlockingSession() {
        val output = javaClass.getResourceAsStream("/staged-sessions-wrapped.txt")!!
            .bufferedReader().use { it.readText() }
        assertEquals(listOf(495866443), Safety.readySessions(output))
        assertEquals(listOf(495866443), Safety.pendingSessions(output))
    }
    @Test fun wrappedFieldsStayWithinTheirOwnSession() {
        val records = listOf(
            "sessionId = 81; appPackageName = com.android.systemui; isStaged = true; isReady = true; isApplied = false; isFailed = false; errorMsg = ;",
            "sessionId = 82; appPackageName = com.other.app; isStaged = true; isReady = true; isApplied = false; isFailed = false; errorMsg = ;",
            "sessionId = 83; appPackageName = com.android.systemui; isStaged = true; isReady = false; isApplied = false; isFailed = true; errorMsg = already staged;",
            "sessionId = 84; appPackageName = com.android.systemui; isStaged = true; isReady = false; isApplied = false; isFailed = false; errorMsg = ;",
            "sessionId = 85; appPackageName = com.android.systemui; isStaged = true; isReady = false; isApplied = true; isFailed = false; errorMsg = ;"
        )
        for (width in listOf(40, 60, 80, 120, 140)) {
            val output = "ROOT_UID=0\r\nRUNNING_MATCH=true\r\n" + records.joinToString("\r\n") { it.chunked(width).joinToString("\r\n    ") }
            assertEquals("width=$width", listOf(81), Safety.readySessions(output))
            assertEquals("width=$width", listOf(81, 84), Safety.pendingSessions(output))
        }
    }
    @Test fun shellArgumentsKeepMetacharactersLiteral() {
        assertEquals("'a'\\''b'", Safety.quote("a'b"))
        assertEquals("'\$(id); reboot'", Safety.quote("\$(id); reboot"))
        for (value in listOf("a\nb", "a\rb", "a\u0000b")) {
            assertThrows(IllegalArgumentException::class.java) { Safety.quote(value) }
        }
    }
}
