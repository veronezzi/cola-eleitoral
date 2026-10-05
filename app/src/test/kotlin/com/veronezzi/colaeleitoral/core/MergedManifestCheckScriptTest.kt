package com.veronezzi.colaeleitoral.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * scripts/check-merged-manifest.sh, which CI runs on the merged release manifest (S3, S11).
 * Needs bash and python3 (present on the CI runner); skipped where they are missing.
 */
class MergedManifestCheckScriptTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** Unit tests run in the module directory (app/). */
    private val script = File("../scripts/check-merged-manifest.sh").absoluteFile

    @Before
    fun requireTools() {
        assumeTrue("script missing: $script", script.isFile)
        assumeTrue("bash and python3 are needed", runs("bash", "--version") && runs("python3", "--version"))
    }

    @Test
    fun theAllowedPermissionsPass() {
        val (code, output) = check(manifest(ALLOWED.map(::usesPermission)))

        assertEquals(output, 0, code)
        assertTrue(output, output.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
    }

    @Test
    fun aPermissionAddedByALibraryFails() {
        for (extra in listOf("android.permission.FOREGROUND_SERVICE", "com.google.android.gms.permission.AD_ID")) {
            val (code, output) = check(manifest(ALLOWED.map(::usesPermission) + usesPermission(extra)))

            assertEquals(output, 1, code)
            assertTrue(output, output.contains(extra))
        }
    }

    @Test
    fun removedComponentsMustStayRemoved() {
        val service = """<service android:name="androidx.work.impl.foreground.SystemForegroundService" />"""
        val emoji = """<provider android:name="androidx.startup.InitializationProvider" android:authorities="x">
            <meta-data android:name="androidx.emoji2.text.EmojiCompatInitializer" android:value="androidx.startup" />
            </provider>"""

        for (component in listOf(service, emoji)) {
            val (code, output) = check(manifest(ALLOWED.map(::usesPermission), application = component))
            assertEquals(output, 1, code)
        }
    }

    @Test
    fun aMissingManifestIsAUsageError() {
        assertEquals(2, run(File(temporaryFolder.root, "absent.xml")).first)
    }

    private fun check(text: String): Pair<Int, String> =
        run(temporaryFolder.newFile().apply { writeText(text) })

    private fun run(manifest: File): Pair<Int, String> {
        val process = ProcessBuilder("bash", script.path, manifest.path).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue("script timed out", process.waitFor(30, TimeUnit.SECONDS))
        return process.exitValue() to output
    }

    private fun runs(vararg command: String): Boolean = try {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
    } catch (e: IOException) {
        false
    }

    private fun usesPermission(name: String) = """<uses-permission android:name="$name" />"""

    private fun manifest(permissions: List<String>, application: String = "") = """<?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$PACKAGE">
            ${permissions.joinToString("\n")}
            <permission android:name="$PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" android:protectionLevel="signature" />
            <application android:name=".ColaEleitoralApplication">$application</application>
        </manifest>
    """.trimIndent()

    private companion object {
        const val PACKAGE = "com.veronezzi.colaeleitoral"
        val ALLOWED = listOf(
            "android.permission.INTERNET",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.USE_BIOMETRIC",
            "android.permission.USE_FINGERPRINT",
            "android.permission.WAKE_LOCK",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "$PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
    }
}
