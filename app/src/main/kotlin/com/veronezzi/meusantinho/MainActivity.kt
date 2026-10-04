package com.veronezzi.meusantinho

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.veronezzi.meusantinho.ui.AppUiState
import com.veronezzi.meusantinho.ui.AppViewModel
import com.veronezzi.meusantinho.ui.MeuSantinhoApp
import com.veronezzi.meusantinho.ui.screens.cola.ColaExport
import com.veronezzi.meusantinho.ui.theme.MeuSantinhoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        if (savedInstanceState == null) {
            // Images of the cola shared in a previous session are temporary (ARCHITECTURE.md 4.7).
            lifecycleScope.launch(Dispatchers.IO) { ColaExport.clearSharedFiles(applicationContext) }
        }
        setContent {
            MeuSantinhoTheme {
                MeuSantinhoApp(viewModel = appViewModel)
            }
        }
    }
}
