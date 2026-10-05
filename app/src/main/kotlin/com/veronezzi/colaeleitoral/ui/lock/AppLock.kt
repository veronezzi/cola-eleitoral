package com.veronezzi.colaeleitoral.ui.lock

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.ui.components.ScreenPreviews
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme

/**
 * Device authentication for the optional app lock (ARCHITECTURE.md 4.10). The lock is only a UI
 * gate: the picks' key does not depend on it, so changing fingerprints never loses picks.
 *
 * Android 11+: strong biometrics or the device credential. Android 8 to 10: weak biometrics or the
 * device credential (strong + credential is not supported there); on 8.x androidx.biometric
 * falls back to KeyguardManager's confirm-credential screen by itself.
 */
object AppLockAuthenticator {
    fun allowedAuthenticators(sdk: Int = Build.VERSION.SDK_INT): Int =
        if (sdk >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** True when the device has a screen lock (PIN, pattern, password or biometrics). */
    fun isAvailable(context: Context): Boolean {
        val secure = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        val canAuthenticate = BiometricManager.from(context).canAuthenticate(allowedAuthenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS
        return secure || canAuthenticate
    }

    /**
     * Shows the system prompt. [onResult] receives true on success and false when the user
     * cancels or the prompt fails; a single wrong attempt keeps the prompt open.
     */
    fun authenticate(activity: FragmentActivity, title: String, subtitle: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators())
            .build()
        prompt.authenticate(info)
    }
}

/**
 * Shown instead of the app while locked: nothing else is composed, so no pick reaches the screen
 * or the accessibility tree. The prompt opens by itself once; the button reopens it.
 *
 * If the device has no screen lock any more (the user removed it after turning the app lock on),
 * the prompt can never succeed and Settings sit behind the lock. The screen then explains it and
 * offers the Android security settings, or turning the app lock off after a warning, instead of
 * leaving "clear the app's data" (and losing the picks) as the only way out. Availability is
 * checked again whenever the screen resumes, so coming back from the settings just works.
 */
@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit,
    onTurnOffLock: () -> Unit,
    modifier: Modifier = Modifier,
    turnOffFailed: Boolean = false,
) {
    val activity = LocalActivity.current as? FragmentActivity
    val context = LocalContext.current
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    val currentOnUnlocked by rememberUpdatedState(onUnlocked)
    var failed by rememberSaveable { mutableStateOf(false) }
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    var available by remember { mutableStateOf(AppLockAuthenticator.isAvailable(context)) }
    var confirmTurnOff by rememberSaveable { mutableStateOf(false) }
    val unlock = {
        if (activity != null) {
            AppLockAuthenticator.authenticate(activity, title, subtitle) { success ->
                if (success) {
                    currentOnUnlocked()
                } else {
                    failed = true
                    available = AppLockAuthenticator.isAvailable(context)
                }
            }
        }
    }
    LifecycleResumeEffect(Unit) {
        available = AppLockAuthenticator.isAvailable(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(available) {
        if (available && !autoPrompted) {
            autoPrompted = true
            unlock()
        }
    }
    LockedContent(
        failed = failed,
        available = available,
        turnOffFailed = turnOffFailed,
        onUnlock = unlock,
        onOpenSecuritySettings = { openSecuritySettings(context) },
        onTurnOff = { confirmTurnOff = true },
        modifier = modifier,
    )
    if (confirmTurnOff) {
        AlertDialog(
            onDismissRequest = { confirmTurnOff = false },
            icon = { Icon(Icons.Outlined.LockOpen, contentDescription = null) },
            title = { Text(stringResource(R.string.lock_turn_off_title)) },
            text = { Text(stringResource(R.string.lock_turn_off_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmTurnOff = false
                        onTurnOffLock()
                    },
                ) { Text(stringResource(R.string.lock_turn_off_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTurnOff = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun LockedContent(
    failed: Boolean,
    available: Boolean,
    turnOffFailed: Boolean,
    onUnlock: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(56.dp))
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            val message = when {
                !available -> R.string.lock_unavailable_message
                failed -> R.string.lock_failed
                else -> R.string.lock_message
            }
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (available) {
                Button(onClick = onUnlock) { Text(stringResource(R.string.lock_unlock)) }
            } else {
                Button(onClick = onOpenSecuritySettings) { Text(stringResource(R.string.lock_open_security_settings)) }
                OutlinedButton(onClick = onTurnOff) { Text(stringResource(R.string.lock_turn_off)) }
            }
            if (turnOffFailed) {
                Text(
                    text = stringResource(R.string.lock_turn_off_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/**
 * Android security settings, where the screen lock is set up; the main Settings screen on builds
 * without it. Returns false when neither exists (nothing else to offer).
 */
private fun openSecuritySettings(context: Context): Boolean =
    listOf(Settings.ACTION_SECURITY_SETTINGS, Settings.ACTION_SETTINGS).any { action ->
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

@ScreenPreviews
@Composable
private fun AppLockNoScreenLockPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        LockedContent(
            failed = true,
            available = false,
            turnOffFailed = false,
            onUnlock = {},
            onOpenSecuritySettings = {},
            onTurnOff = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun AppLockPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        LockedContent(
            failed = false,
            available = true,
            turnOffFailed = false,
            onUnlock = {},
            onOpenSecuritySettings = {},
            onTurnOff = {},
        )
    }
}
