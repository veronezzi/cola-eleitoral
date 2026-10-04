package com.veronezzi.meusantinho.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.veronezzi.meusantinho.R

/** Starts the reminder opt-in flow (see [rememberReminderOptIn]). */
@Stable
class ReminderOptInLauncher internal constructor(private val onStart: () -> Unit) {
    fun start() = onStart()
}

private enum class OptInStep { IDLE, RATIONALE, NOTIFICATIONS_BLOCKED }

/**
 * Opt-in for the election-day reminder (ARCHITECTURE.md 4.8): first a rationale dialog, then, on
 * Android 13+, the runtime `POST_NOTIFICATIONS` request in context. [onEnabled] runs only when
 * the reminder can actually be shown; when the permission is denied, or notifications are off in
 * the system, a dialog explains how to allow them in the Android settings.
 */
@Composable
fun rememberReminderOptIn(onEnabled: () -> Unit): ReminderOptInLauncher {
    val context = LocalContext.current
    val currentOnEnabled by rememberUpdatedState(onEnabled)
    var step by rememberSaveable { mutableStateOf(OptInStep.IDLE) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        step = if (granted) {
            currentOnEnabled()
            OptInStep.IDLE
        } else {
            OptInStep.NOTIFICATIONS_BLOCKED
        }
    }

    when (step) {
        OptInStep.RATIONALE -> AlertDialog(
            onDismissRequest = { step = OptInStep.IDLE },
            icon = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
            title = { Text(stringResource(R.string.optin_reminder_title)) },
            text = { Text(stringResource(R.string.optin_reminder_rationale)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            needsPermissionRequest(context) -> {
                                step = OptInStep.IDLE
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            NotificationManagerCompat.from(context).areNotificationsEnabled() -> {
                                currentOnEnabled()
                                step = OptInStep.IDLE
                            }
                            else -> step = OptInStep.NOTIFICATIONS_BLOCKED
                        }
                    },
                ) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { step = OptInStep.IDLE }) { Text(stringResource(R.string.action_not_now)) }
            },
        )
        OptInStep.NOTIFICATIONS_BLOCKED -> AlertDialog(
            onDismissRequest = { step = OptInStep.IDLE },
            title = { Text(stringResource(R.string.optin_blocked_title)) },
            text = { Text(stringResource(R.string.optin_blocked_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        step = OptInStep.IDLE
                        openNotificationSettings(context)
                    },
                ) { Text(stringResource(R.string.action_open_android_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { step = OptInStep.IDLE }) { Text(stringResource(R.string.action_close)) }
            },
        )
        OptInStep.IDLE -> Unit
    }

    return remember { ReminderOptInLauncher { step = OptInStep.RATIONALE } }
}

private fun needsPermissionRequest(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Some OEM builds lack this screen; nothing else to offer.
    }
}
