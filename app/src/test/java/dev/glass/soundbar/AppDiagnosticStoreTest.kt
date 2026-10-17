package dev.glass.soundbar

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppDiagnosticStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun crashAndStagesSurviveAProcessRestartAndAreIncludedInExport() {
        val directory = folder.newFolder("app-diagnostics")
        AppDiagnosticStore(directory).apply {
            event("native-sheet.content.attach")
            fatal("Last stage: native-sheet.content.attach\njava.lang.IllegalStateException: original cause\n at original.Frame.attach(Frame.kt:12)")
        }
        val output = ByteArrayOutputStream()
        AppDiagnosticStore(directory).apply { event("app.start"); export(output) }
        val text = output.toString("UTF-8")
        assertTrue(text.contains("native-sheet.content.attach"))
        assertTrue(text.contains("original cause"))
        assertTrue(text.contains("original.Frame.attach"))
        assertTrue(text.contains("app.start"))
    }

    @Test fun newestFatalReportReplacesPreviousReportWithoutLosingEventHistory() {
        val store = AppDiagnosticStore(folder.newFolder("replace-crash"))
        store.event("first stage")
        store.fatal("old cause")
        store.fatal("new cause")
        val output = ByteArrayOutputStream()
        store.export(output)
        val text = output.toString("UTF-8")
        assertTrue(text.contains("first stage"))
        assertTrue(text.contains("new cause"))
        assertFalse(text.contains("old cause"))
    }
}
