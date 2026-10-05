package com.veronezzi.colaeleitoral.ui.screens.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.veronezzi.colaeleitoral.BuildConfig
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.repository.ElectionRepository
import com.veronezzi.colaeleitoral.ui.common.TseLinks
import com.veronezzi.colaeleitoral.ui.common.formatDayMonthTime
import com.veronezzi.colaeleitoral.ui.common.openInBrowser
import com.veronezzi.colaeleitoral.ui.common.writeEmail
import com.veronezzi.colaeleitoral.ui.components.AppTopBar
import com.veronezzi.colaeleitoral.ui.components.ScreenPreviews
import com.veronezzi.colaeleitoral.ui.components.SectionHeader
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

/** Last time TSE data was downloaded on this device, for "Sobre". */
@HiltViewModel
class AboutViewModel @Inject constructor(electionRepository: ElectionRepository) : ViewModel() {
    val lastUpdate: StateFlow<Instant?> = electionRepository.observeElections()
        .map { it.fetchedAt }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class AboutActions(
    val onOpenPrivacy: () -> Unit,
    val onOpenLicenses: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
fun AboutRouteScreen(actions: AboutActions, viewModel: AboutViewModel = hiltViewModel()) {
    val lastUpdate by viewModel.lastUpdate.collectAsStateWithLifecycle()
    AboutScreen(lastUpdate = lastUpdate, actions = actions)
}

/**
 * "Sobre" (ARCHITECTURE.md 4.9): independence notice, data source with link and the CC BY
 * attribution, what the TSE receives on each query, how the election is chosen, version, who
 * develops the app and the contact. Developer name, contact and the privacy-policy URL come only
 * from BuildConfig (Gradle properties).
 */
@Composable
fun AboutScreen(
    lastUpdate: Instant?,
    actions: AboutActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mailSubject = stringResource(R.string.about_contact_subject)
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(R.string.about_title)) },
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
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                Paragraphs(
                    stringResource(R.string.onboarding_independent),
                    stringResource(R.string.about_no_logos),
                )
                SectionHeader(text = stringResource(R.string.about_source_section))
                Paragraphs(
                    stringResource(R.string.about_source),
                    stringResource(R.string.about_attribution),
                    stringResource(R.string.about_open_data_fallback),
                    lastUpdate?.let { stringResource(R.string.about_last_update, it.formatDayMonthTime()) }
                        ?: stringResource(R.string.about_last_update_never),
                )
                LinkRow(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    title = stringResource(R.string.about_open_divulga),
                    supporting = TseLinks.DIVULGA_HOME,
                    onClick = { context.openInBrowser(TseLinks.DIVULGA_HOME) },
                )
                LinkRow(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    title = stringResource(R.string.about_open_open_data),
                    supporting = TseLinks.OPEN_DATA_HOME,
                    onClick = { context.openInBrowser(TseLinks.OPEN_DATA_HOME) },
                )
                SectionHeader(text = stringResource(R.string.about_privacy_section))
                Paragraphs(
                    stringResource(R.string.privacy_tse_query_notice),
                    stringResource(R.string.onboarding_privacy),
                )
                LinkRow(
                    icon = Icons.Outlined.Policy,
                    title = stringResource(R.string.privacy_title),
                    supporting = null,
                    onClick = actions.onOpenPrivacy,
                )
                SectionHeader(text = stringResource(R.string.about_how_section))
                Paragraphs(stringResource(R.string.about_how_election), stringResource(R.string.about_neutrality))
                SectionHeader(text = stringResource(R.string.about_app_section))
                Paragraphs(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    stringResource(R.string.about_developer, BuildConfig.DEVELOPER_NAME),
                )
                LinkRow(
                    icon = Icons.Outlined.Mail,
                    title = stringResource(R.string.about_contact),
                    supporting = BuildConfig.CONTACT_EMAIL,
                    onClick = { context.writeEmail(BuildConfig.CONTACT_EMAIL, mailSubject) },
                )
                LinkRow(
                    icon = Icons.Outlined.Code,
                    title = stringResource(R.string.licenses_title),
                    supporting = null,
                    onClick = actions.onOpenLicenses,
                )
                LinkRow(
                    icon = Icons.Outlined.Settings,
                    title = stringResource(R.string.settings_title),
                    supporting = null,
                    onClick = actions.onOpenSettings,
                )
            }
        }
    }
}

@Composable
internal fun Paragraphs(vararg texts: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        texts.forEach { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
internal fun LinkRow(icon: ImageVector, title: String, supporting: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
    HorizontalDivider()
}

@ScreenPreviews
@Composable
private fun AboutScreenPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        AboutScreen(lastUpdate = Instant.now(), actions = AboutActions({}, {}, {}))
    }
}
