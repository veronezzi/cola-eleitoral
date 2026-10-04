package com.veronezzi.meusantinho.ui.common

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Reference-counted `FLAG_SECURE` for the activity window (ARCHITECTURE.md 5.6). Screens that show
 * picks acquire it while visible; with list and detail side by side, the flag stays until the last
 * one leaves. It blocks screenshots and screen recording and blanks the recents thumbnail; on
 * Android 13+ the recents screenshot is also disabled. Exporting the cola does not depend on
 * screen capture, so sharing keeps working.
 */
class SecureWindowController(private val activity: Activity) {
    private var holders = 0

    fun acquire() {
        holders += 1
        if (holders == 1) apply(secure = true)
    }

    fun release() {
        if (holders == 0) return
        holders -= 1
        if (holders == 0) apply(secure = false)
    }

    private fun apply(secure: Boolean) {
        if (secure) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!secure)
        }
    }
}

/** Null in previews and tests without an activity: [SecureScreen] then does nothing. */
val LocalSecureWindowController = staticCompositionLocalOf<SecureWindowController?> { null }

/** The user's "Proteger telas com escolhas" setting (on by default). */
val LocalSecureScreensEnabled = compositionLocalOf { true }

/**
 * Marks the calling screen as showing picks while [active]: the window becomes secure if the
 * user kept the protection on.
 */
@Composable
fun SecureScreen(active: Boolean = true) {
    val controller = LocalSecureWindowController.current ?: return
    val secure = active && LocalSecureScreensEnabled.current
    DisposableEffect(controller, secure) {
        if (secure) controller.acquire()
        onDispose {
            if (secure) controller.release()
        }
    }
}
