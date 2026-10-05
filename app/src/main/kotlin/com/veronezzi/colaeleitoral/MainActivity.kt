package com.veronezzi.colaeleitoral

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.veronezzi.colaeleitoral.ui.AppUiState
import com.veronezzi.colaeleitoral.ui.AppViewModel
import com.veronezzi.colaeleitoral.ui.ColaEleitoralApp
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity hosting the Compose UI, edge-to-edge with predictive back (targetSdk 36).
 * Extends [FragmentActivity] because androidx.biometric's BiometricPrompt (optional app lock)
 * needs one.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { appViewModel.uiState.value is AppUiState.Loading }
        enableEdgeToEdge()
        setContent {
            ColaEleitoralTheme {
                ColaEleitoralApp(viewModel = appViewModel)
            }
        }
    }
}
