package com.veronezzi.colaeleitoral.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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
 */
@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as? FragmentActivity
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    val currentOnUnlocked by rememberUpdatedState(onUnlocked)
    var failed by rememberSaveable { mutableStateOf(false) }
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    val unlock = {
        if (activity != null) {
            AppLockAuthenticator.authenticate(activity, title, subtitle) { success ->
                if (success) currentOnUnlocked() else failed = true
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!autoPrompted) {
            autoPrompted = true
            unlock()
        }
    }
    LockedContent(failed = failed, onUnlock = unlock, modifier = modifier)
}

@Composable
private fun LockedContent(failed: Boolean, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
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
            Text(
                text = stringResource(if (failed) R.string.lock_failed else R.string.lock_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Button(onClick = onUnlock) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}

@ScreenPreviews
@Composable
private fun AppLockPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        LockedContent(failed = false, onUnlock = {})
    }
}
