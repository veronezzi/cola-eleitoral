package com.veronezzi.colaeleitoral.ui.screens.cola

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.ui.common.SecureScreen
import com.veronezzi.colaeleitoral.ui.common.formatDate
import com.veronezzi.colaeleitoral.ui.common.slotLabel
import com.veronezzi.colaeleitoral.ui.common.spokenDigits
import com.veronezzi.colaeleitoral.ui.components.AppTopBar
import com.veronezzi.colaeleitoral.ui.components.ListSkeleton
import com.veronezzi.colaeleitoral.ui.components.ScreenPreviews
import com.veronezzi.colaeleitoral.ui.navigation.ColaExportRoute
import com.veronezzi.colaeleitoral.ui.preview.PreviewData
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ColaExportRouteScreen(
    route: ColaExportRoute,
    onBack: () -> Unit,
    viewModel: ColaExportViewModel = hiltViewModel<ColaExportViewModel, ColaExportViewModel.Factory> { it.create(route) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ColaExportScreen(state = state, onBack = onBack, onShowNamesChange = viewModel::onShowNamesChange)
}

/** Builds the localized [ColaContent] of [state]. */
@Composable
fun rememberColaContent(state: ColaUiState): ColaContent {
    val roundText = stringResource(if (state.round == Round.FIRST) R.string.round_first else R.string.round_second)
    val title = stringResource(
        R.string.cola_title,
        state.electionName.ifBlank { stringResource(R.string.cola_untitled_election) },
        roundText,
        state.date?.formatDate().orEmpty(),
    ).trimEnd(',', ' ')
    val lines = state.slots.map { slot ->
        ColaLine(
            label = stringResource(R.string.ballot_vote_label, slot.voteNumber, slotLabel(slot.office.name, slot.slot, slot.hasSeveralSeats)),
            digits = slot.pick?.candidateNumber,
            digitCount = slot.office.digitCount,
            nameLine = slot.pick?.takeIf { state.showNames }?.let {
                stringResource(R.string.pick_name_and_party, it.ballotName, it.partyAcronym)
            },
        )
    }
    val footer = listOf(stringResource(R.string.cola_footer_booth), stringResource(R.string.cola_footer_source))
    return remember(title, lines, footer) { ColaContent(title, lines, footer) }
}

@Composable
fun ColaExportScreen(
    state: ColaUiState,
    onBack: () -> Unit,
    onShowNamesChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SecureScreen()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmShare by rememberSaveable { mutableStateOf(false) }
    val content = rememberColaContent(state)
    val jobName = stringResource(R.string.cola_print_job)
    val chooserTitle = stringResource(R.string.cola_share_chooser)
    val shareFailed = stringResource(R.string.cola_share_failed)
    val preview by produceState<Bitmap?>(initialValue = null, content) {
        value = withContext(Dispatchers.Default) { ColaRenderer().renderBitmap(content) }
    }
    val previewDescription = stringResource(
        R.string.cola_preview_description,
        content.title,
        content.lines.joinToString("; ") { line ->
            listOfNotNull(line.label, line.digits?.let { spokenDigits(it) }, line.nameLine).joinToString(", ")
        },
    )

    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(R.string.cola_screen_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            ListSkeleton(description = stringResource(R.string.ballot_loading), modifier = Modifier.padding(padding), rows = 3)
            return@Scaffold
        }
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(R.string.cola_intro), style = MaterialTheme.typography.bodyLarge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ColaRenderer.IMAGE_WIDTH / ColaRenderer.IMAGE_HEIGHT.toFloat())
                        .border(1.dp, MaterialTheme.colorScheme.outline),
                    contentAlignment = Alignment.Center,
                ) {
                    preview?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = previewDescription,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = state.showNames, role = Role.Switch, onValueChange = onShowNamesChange),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cola_show_names), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.cola_show_names_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.showNames, onCheckedChange = null)
                }
                Button(
                    onClick = { ColaExport.print(context, content, jobName) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.cola_print))
                }
                OutlinedButton(onClick = { confirmShare = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.cola_share))
                }
                Text(
                    text = stringResource(R.string.cola_booth_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmShare) {
        AlertDialog(
            onDismissRequest = { confirmShare = false },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text(stringResource(R.string.cola_share_warning_title)) },
            text = { Text(stringResource(R.string.cola_share_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmShare = false
                        scope.launch {
                            val file = withContext(Dispatchers.IO) { ColaExport.writeImage(context, content) }
                            if (!ColaExport.share(context, file, chooserTitle)) snackbarHostState.showSnackbar(shareFailed)
                        }
                    },
                ) { Text(stringResource(R.string.cola_share_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmShare = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@ScreenPreviews
@Composable
private fun ColaExportPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        ColaExportScreen(
            state = ColaUiState(
                isLoading = false,
                electionName = PreviewData.election.name,
                date = PreviewData.election.date,
                slots = com.veronezzi.colaeleitoral.ui.common.buildBallotSlots(PreviewData.offices, PreviewData.picks),
            ),
            onBack = {},
            onShowNamesChange = {},
        )
    }
}
