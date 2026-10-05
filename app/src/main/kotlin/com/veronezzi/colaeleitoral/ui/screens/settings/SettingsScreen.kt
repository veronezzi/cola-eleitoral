package com.veronezzi.colaeleitoral.ui.screens.settings

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.ui.components.AppTopBar
import com.veronezzi.colaeleitoral.ui.components.ScreenPreviews
import com.veronezzi.colaeleitoral.ui.components.SectionHeader
import com.veronezzi.colaeleitoral.ui.components.rememberReminderOptIn
import com.veronezzi.colaeleitoral.ui.lock.AppLockAuthenticator
import com.veronezzi.colaeleitoral.ui.screens.cola.ColaExport
import com.veronezzi.colaeleitoral.ui.screens.home.locationLabel
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsRouteScreen(
    onBack: () -> Unit,
    onChangeLocation: () -> Unit,
    onDataDeleted: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val promptTitle = stringResource(R.string.lock_enable_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_prompt_subtitle)
    val reminderOptIn = rememberReminderOptIn(onEnabled = viewModel::onReminderEnabled)
    LaunchedEffect(state.dataDeleted) {
        if (state.dataDeleted) {
            viewModel.onDataDeletedHandled()
            onDataDeleted()
        }
    }
    SettingsScreen(
        state = state,
        onBack = onBack,
        onChangeLocation = onChangeLocation,
        onReminderChange = { enabled -> if (enabled) reminderOptIn.start() else viewModel.onReminderDisabled() },
        onAppLockChange = { enabled ->
            when {
                !enabled -> viewModel.onAppLockDisabled()
                activity == null || !AppLockAuthenticator.isAvailable(context) -> viewModel.onAppLockUnavailable()
                // Turning the lock on requires one successful prompt, so nobody locks themselves out.
                else -> AppLockAuthenticator.authenticate(activity, promptTitle, promptSubtitle) { success ->
                    if (success) viewModel.onAppLockEnabled()
                }
            }
        },
        onSecureScreensChange = viewModel::onSecureScreensChange,
        onConfirmationRequested = viewModel::onConfirmationRequested,
        onConfirmationDismissed = viewModel::onConfirmationDismissed,
        onConfirmed = { viewModel.onConfirmed { clearDownloadedFiles(context) } },
        onMessageShown = viewModel::onMessageShown,
    )
}

/** Photos cached by Coil and leftover shared images are downloaded or derived data. */
private suspend fun clearDownloadedFiles(context: Context) {
    withContext(Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
        ColaExport.clearSharedFiles(context)
    }
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onChangeLocation: () -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onSecureScreensChange: (Boolean) -> Unit,
    onConfirmationRequested: (SettingsConfirmation) -> Unit,
    onConfirmationDismissed: () -> Unit,
    onConfirmed: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    state.message?.let { message ->
        val text = stringResource(
            when (message) {
                SettingsMessage.DOWNLOADS_CLEARED -> R.string.settings_downloads_cleared
                SettingsMessage.LOCK_UNAVAILABLE -> R.string.settings_lock_unavailable
                SettingsMessage.SAVE_FAILED -> R.string.settings_save_failed
                SettingsMessage.DELETE_FAILED -> R.string.settings_delete_failed
            },
        )
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(text, withDismissAction = true)
            onMessageShown()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionHeader(text = stringResource(R.string.settings_section_place))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_location)) },
                    supportingContent = {
                        Text(state.location?.let { locationLabel(it) } ?: stringResource(R.string.settings_location_none))
                    },
                    leadingContent = { Icon(Icons.Outlined.Place, contentDescription = null) },
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.home_change_location),
                        onClick = onChangeLocation,
                    ),
                )
                HorizontalDivider()

                SectionHeader(text = stringResource(R.string.settings_section_reminder))
                SwitchRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = stringResource(R.string.settings_reminder),
                    description = stringResource(R.string.settings_reminder_description),
                    checked = state.reminderEnabled,
                    onCheckedChange = onReminderChange,
                )
                HorizontalDivider()

                SectionHeader(text = stringResource(R.string.settings_section_privacy))
                SwitchRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = stringResource(R.string.settings_app_lock),
                    description = stringResource(R.string.settings_app_lock_description),
                    checked = state.appLockEnabled,
                    onCheckedChange = onAppLockChange,
                )
                SwitchRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = stringResource(R.string.settings_secure_screens),
                    description = stringResource(R.string.settings_secure_screens_description),
                    checked = state.secureScreens,
                    onCheckedChange = onSecureScreensChange,
                )
                HorizontalDivider()

                SectionHeader(text = stringResource(R.string.settings_section_data))
                ActionRow(
                    icon = Icons.Outlined.CleaningServices,
                    title = stringResource(R.string.settings_clear_downloads),
                    description = stringResource(R.string.settings_clear_downloads_description),
                    enabled = !state.isBusy,
                    onClick = { onConfirmationRequested(SettingsConfirmation.CLEAR_DOWNLOADS) },
                )
                ActionRow(
                    icon = Icons.Outlined.DeleteForever,
                    title = stringResource(R.string.settings_delete_all),
                    description = stringResource(R.string.settings_delete_all_description),
                    enabled = !state.isBusy,
                    destructive = true,
                    onClick = { onConfirmationRequested(SettingsConfirmation.DELETE_ALL) },
                )
            }
        }
    }
    state.confirmation?.let { confirmation ->
        val (title, message, confirm) = when (confirmation) {
            SettingsConfirmation.CLEAR_DOWNLOADS -> Triple(
                R.string.settings_clear_downloads,
                R.string.settings_clear_downloads_confirm_message,
                R.string.settings_clear_downloads_confirm,
            )
            SettingsConfirmation.DELETE_ALL -> Triple(
                R.string.settings_delete_all,
                R.string.settings_delete_all_confirm_message,
                R.string.settings_delete_all_confirm,
            )
        }
        AlertDialog(
            onDismissRequest = onConfirmationDismissed,
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(message)) },
            confirmButton = { TextButton(onClick = onConfirmed) { Text(stringResource(confirm)) } },
            dismissButton = { TextButton(onClick = onConfirmationDismissed) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(headlineColor = color, leadingIconColor = color),
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

@ScreenPreviews
@Composable
private fun SettingsScreenPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        SettingsScreen(
            state = SettingsUiState(location = VoterLocation("SP"), reminderEnabled = true),
            onBack = {},
            onChangeLocation = {},
            onReminderChange = {},
            onAppLockChange = {},
            onSecureScreensChange = {},
            onConfirmationRequested = {},
            onConfirmationDismissed = {},
            onConfirmed = {},
            onMessageShown = {},
        )
    }
}
