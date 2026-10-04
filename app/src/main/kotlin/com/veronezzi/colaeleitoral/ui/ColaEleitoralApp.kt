package com.veronezzi.colaeleitoral.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.ui.common.LocalSecureScreensEnabled
import com.veronezzi.colaeleitoral.ui.common.LocalSecureWindowController
import com.veronezzi.colaeleitoral.ui.common.SecureWindowController
import com.veronezzi.colaeleitoral.ui.lock.AppLockScreen
import com.veronezzi.colaeleitoral.ui.navigation.AppNavigation

/**
 * Root of the UI. While the optional lock is closed the navigation is not composed at all; its
 * back stack (NavController) and saved UI state survive the lock and come back intact.
 */
@Composable
fun ColaEleitoralApp(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val secureController = remember(activity) { activity?.let { SecureWindowController(it) } }

    DisposableEffect(viewModel) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = viewModel.onAppForegrounded()

            override fun onStop(owner: LifecycleOwner) = viewModel.onAppBackgrounded()
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    when (val current = state) {
        AppUiState.Loading -> StartupScreen()
        is AppUiState.Ready -> CompositionLocalProvider(
            LocalSecureWindowController provides secureController,
            LocalSecureScreensEnabled provides current.secureScreens,
        ) {
            val navController = rememberNavController()
            val stateHolder = rememberSaveableStateHolder()
            if (current.isLocked) {
                AppLockScreen(onUnlocked = viewModel::onUnlocked)
            } else {
                stateHolder.SaveableStateProvider(key = "app") {
                    AppNavigation(
                        navController = navController,
                        startDestination = current.startDestination,
                        ballotTarget = current.ballotTarget,
                    )
                }
            }
            if (current.picksLost && !current.isLocked) {
                AlertDialog(
                    onDismissRequest = viewModel::onPicksLostAcknowledged,
                    title = { Text(stringResource(R.string.picks_lost_title)) },
                    text = { Text(stringResource(R.string.picks_lost_message)) },
                    confirmButton = {
                        TextButton(onClick = viewModel::onPicksLostAcknowledged) { Text(stringResource(R.string.action_ok)) }
                    },
                )
            }
        }
    }
}

/** Shown for the few milliseconds before the settings are read (the splash usually covers it). */
@Composable
private fun StartupScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        }
    }
}
