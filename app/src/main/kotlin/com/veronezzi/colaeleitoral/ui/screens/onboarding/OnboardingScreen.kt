package com.veronezzi.colaeleitoral.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.ui.common.TseLinks
import com.veronezzi.colaeleitoral.ui.common.openInBrowser
import com.veronezzi.colaeleitoral.ui.common.presentation
import com.veronezzi.colaeleitoral.ui.components.ScreenPreviews
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme

@Composable
fun OnboardingRoute(
    onContinue: (OnboardingNext) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.next) {
        state.next?.let { next ->
            viewModel.onNavigated()
            onContinue(next)
        }
    }
    OnboardingScreen(isSaving = state.isSaving, error = state.error, onAccept = viewModel::onAccept)
}

/**
 * Independence notice (ARCHITECTURE.md 4.2; Google Play "government information" policy): the
 * app is not the TSE nor a government body, where the data comes from, what the TSE receives on
 * each query (LGPD art. 9º) and that picks stay on the device. No TSE or Justiça Eleitoral logos,
 * no Brasão.
 */
@Composable
fun OnboardingScreen(
    isSaving: Boolean,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    error: AppError? = null,
) {
    val context = LocalContext.current
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                NoticeParagraph(icon = Icons.Outlined.Info, text = stringResource(R.string.onboarding_independent))
                NoticeParagraph(icon = Icons.Outlined.Source, text = stringResource(R.string.onboarding_source))
                OutlinedButton(onClick = { context.openInBrowser(TseLinks.DIVULGA_HOME) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.onboarding_open_source_site))
                }
                NoticeParagraph(icon = Icons.Outlined.Public, text = stringResource(R.string.privacy_tse_query_notice))
                NoticeParagraph(icon = Icons.Outlined.Lock, text = stringResource(R.string.onboarding_privacy))
                NoticeParagraph(icon = Icons.Outlined.PhoneAndroid, text = stringResource(R.string.onboarding_booth))
                Button(
                    onClick = onAccept,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.onboarding_accept))
                }
                error?.let {
                    Text(
                        text = stringResource(it.presentation().message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeParagraph(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@ScreenPreviews
@Composable
private fun OnboardingScreenPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        OnboardingScreen(isSaving = false, onAccept = {})
    }
}
