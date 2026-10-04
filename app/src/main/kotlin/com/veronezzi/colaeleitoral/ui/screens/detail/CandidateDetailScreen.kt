package com.veronezzi.colaeleitoral.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.domain.model.CandidateDetail
import com.veronezzi.colaeleitoral.domain.model.RunningMate
import com.veronezzi.colaeleitoral.ui.common.SecureScreen
import com.veronezzi.colaeleitoral.ui.common.formatDateTime
import com.veronezzi.colaeleitoral.ui.common.openInBrowser
import com.veronezzi.colaeleitoral.ui.common.presentation
import com.veronezzi.colaeleitoral.ui.common.slotLabel
import com.veronezzi.colaeleitoral.ui.components.AppTopBar
import com.veronezzi.colaeleitoral.ui.components.CandidateAvatar
import com.veronezzi.colaeleitoral.ui.components.DataFreshnessBar
import com.veronezzi.colaeleitoral.ui.components.DigitBoxSize
import com.veronezzi.colaeleitoral.ui.components.DigitBoxes
import com.veronezzi.colaeleitoral.ui.components.ErrorState
import com.veronezzi.colaeleitoral.ui.components.ListSkeleton
import com.veronezzi.colaeleitoral.ui.components.MessageState
import com.veronezzi.colaeleitoral.ui.components.ScreenPreviews
import com.veronezzi.colaeleitoral.ui.components.SectionHeader
import com.veronezzi.colaeleitoral.ui.components.StatusChip
import com.veronezzi.colaeleitoral.ui.navigation.CandidateDetailRoute
import com.veronezzi.colaeleitoral.ui.preview.PreviewData
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme

/** Stateful detail, used as a NavHost destination and as the detail pane of the list. */
@Composable
fun CandidateDetailRouteScreen(
    route: CandidateDetailRoute,
    onBack: (() -> Unit)?,
    onOpenBallot: () -> Unit,
    viewModel: CandidateDetailViewModel = hiltViewModel<CandidateDetailViewModel, CandidateDetailViewModel.Factory>(
        key = "detail:${route.electionId}:${route.round}:${route.officeCode}:${route.slot}:${route.candidateId}",
    ) { it.create(route) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CandidateDetailScreen(
        state = state,
        onBack = onBack,
        onSave = viewModel::onSaveClick,
        onRemove = viewModel::onRemoveClick,
        onConfirmReplace = viewModel::onConfirmReplace,
        onDismissReplace = viewModel::onDismissReplace,
        onUndoRemove = viewModel::onUndoRemove,
        onMessageShown = viewModel::onMessageShown,
        onRetry = viewModel::onRefresh,
        onOpenBallot = onOpenBallot,
    )
}

@Composable
fun CandidateDetailScreen(
    state: CandidateDetailUiState,
    onBack: (() -> Unit)?,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onConfirmReplace: () -> Unit,
    onDismissReplace: () -> Unit,
    onUndoRemove: (BallotPick) -> Unit,
    onMessageShown: () -> Unit,
    onRetry: () -> Unit,
    onOpenBallot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecureScreen(active = state.pickState != PickState.NotSaved)
    val snackbarHostState = remember { SnackbarHostState() }
    val voteLabel = slotLabel(state.officeName, state.slot, state.hasSeveralSeats)
    DetailMessageEffect(
        state = state,
        voteLabel = voteLabel,
        snackbarHostState = snackbarHostState,
        onUndoRemove = onUndoRemove,
        onOpenBallot = onOpenBallot,
        onMessageShown = onMessageShown,
    )
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = state.candidate?.ballotName ?: voteLabel, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val content = state.content) {
                DetailContent.Loading -> ListSkeleton(description = stringResource(R.string.detail_loading), rows = 3, avatarSize = 72.dp)
                is DetailContent.Failed -> ErrorState(
                    error = content.error,
                    onRetry = onRetry,
                    aboutCandidate = true,
                    officialUrl = state.officialPageUrl,
                )
                DetailContent.Loaded -> DetailContentView(state, voteLabel, onSave, onRemove, onRetry)
            }
        }
    }
    state.confirmReplace?.let { current ->
        AlertDialog(
            onDismissRequest = onDismissReplace,
            title = { Text(stringResource(R.string.detail_replace_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.detail_replace_message,
                        current.ballotName,
                        state.candidate?.ballotName.orEmpty(),
                        voteLabel,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = onConfirmReplace) { Text(stringResource(R.string.detail_replace_confirm)) } },
            dismissButton = { TextButton(onClick = onDismissReplace) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DetailMessageEffect(
    state: CandidateDetailUiState,
    voteLabel: String,
    snackbarHostState: SnackbarHostState,
    onUndoRemove: (BallotPick) -> Unit,
    onOpenBallot: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val message = state.message ?: return
    val text = when (message) {
        is DetailMessage.Saved -> message.statusNotice?.let { stringResource(R.string.detail_saved_with_notice, voteLabel, it) }
            ?: stringResource(R.string.detail_saved, voteLabel)
        is DetailMessage.Removed -> stringResource(R.string.detail_removed)
        is DetailMessage.Duplicate -> stringResource(R.string.detail_duplicate, state.officeName)
        is DetailMessage.Failed -> stringResource(R.string.detail_save_failed)
    }
    val action = when (message) {
        is DetailMessage.Saved -> stringResource(R.string.detail_saved_action)
        is DetailMessage.Removed -> stringResource(R.string.action_undo)
        else -> null
    }
    LaunchedEffect(message) {
        val result = snackbarHostState.showSnackbar(
            message = text,
            actionLabel = action,
            withDismissAction = action == null,
            duration = if (message is DetailMessage.Saved && message.statusNotice != null) {
                SnackbarDuration.Long
            } else {
                SnackbarDuration.Short
            },
        )
        if (result == SnackbarResult.ActionPerformed) {
            when (message) {
                is DetailMessage.Removed -> onUndoRemove(message.pick)
                is DetailMessage.Saved -> onOpenBallot()
                else -> Unit
            }
        }
        onMessageShown()
    }
}

@Composable
private fun DetailContentView(
    state: CandidateDetailUiState,
    voteLabel: String,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    val candidate = state.candidate ?: return
    val context = LocalContext.current
    val detail = state.detail
    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CandidateAvatar(
                name = candidate.ballotName,
                photoUrl = state.photoUrl,
                size = 96.dp,
                photoDescription = stringResource(R.string.candidate_photo_description, candidate.ballotName),
            )
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = candidate.ballotName,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                candidate.fullName?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = stringResource(R.string.detail_office_and_unit, voteLabel, state.unitName),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.detail_number_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DigitBoxes(digits = state.numberText, digitCount = state.digitCount, size = DigitBoxSize.Large)
            Text(
                text = pluralStringResource(R.plurals.digit_count, state.digitCount, state.digitCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PickActions(state = state, voteLabel = voteLabel, onSave = onSave, onRemove = onRemove)
            OutlinedButton(
                onClick = { context.openInBrowser(state.officialPageUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.detail_open_tse_page))
            }
        }

        state.detailError?.let { error ->
            Card(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.detail_partial_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(error.presentation(hasCache = true, aboutCandidate = true).message),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (error.presentation().canRetry) {
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                        }
                    }
                }
            }
        }

        SectionHeader(text = stringResource(R.string.detail_party_section))
        InfoLine(
            label = stringResource(R.string.detail_party),
            value = listOfNotNull(
                candidate.party.acronym,
                candidate.party.name?.takeIf { it.isNotBlank() },
                candidate.party.number?.let { stringResource(R.string.detail_party_number, it) },
            ).joinToString(" · "),
        )
        val coalitionType = detail?.coalitionType?.takeIf { it.isNotBlank() }
        candidate.coalition?.takeIf { it.isNotBlank() }?.let { coalition ->
            InfoLine(label = coalitionType ?: stringResource(R.string.detail_coalition), value = coalition)
        }
        detail?.coalitionComposition?.takeIf { it.isNotBlank() }?.let {
            InfoLine(label = stringResource(R.string.detail_coalition_composition), value = it)
        }

        SectionHeader(text = stringResource(R.string.detail_status_section))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.detail_registration),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            StatusChip(text = candidate.status.registration)
        }
        candidate.status.onBallot?.takeIf { it.isNotBlank() }?.let {
            InfoLine(label = stringResource(R.string.detail_on_ballot), value = it)
        }
        candidate.status.totalization?.takeIf { it.isNotBlank() }?.let {
            InfoLine(label = stringResource(R.string.detail_totalization), value = it)
        }

        detail?.runningMates?.takeIf { it.isNotEmpty() }?.let { mates ->
            SectionHeader(text = stringResource(R.string.detail_running_mates))
            mates.forEach { RunningMateRow(it) }
        }

        detail?.lastUpdate?.let {
            Text(
                text = stringResource(R.string.detail_last_update, it.formatDateTime()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        state.freshness?.let { DataFreshnessBar(freshness = it, onRetry = onRetry) }
    }
}

@Composable
private fun PickActions(state: CandidateDetailUiState, voteLabel: String, onSave: () -> Unit, onRemove: () -> Unit) {
    when (val pick = state.pickState) {
        PickState.SavedHere -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.detail_saved_state, voteLabel),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth().testTag(REMOVE_BUTTON_TAG)) {
                Icon(Icons.Outlined.BookmarkRemove, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.detail_remove))
            }
        }
        is PickState.InOtherSlot -> Text(
            text = stringResource(R.string.detail_in_other_slot, state.officeName, pick.otherSlot),
            style = MaterialTheme.typography.bodyMedium,
        )
        else -> if (state.canEdit) {
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag(SAVE_BUTTON_TAG),
            ) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.detail_save))
            }
        } else {
            MessageState(
                icon = Icons.Outlined.PersonSearch,
                title = stringResource(R.string.detail_round_closed_title),
                message = stringResource(R.string.detail_round_closed_message),
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RunningMateRow(mate: RunningMate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CandidateAvatar(name = mate.ballotName, photoUrl = mate.photoUrl)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = mate.role, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = mate.ballotName, style = MaterialTheme.typography.titleMedium)
            mate.partyAcronym?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            mate.status?.takeIf { it.isNotBlank() }?.let { StatusChip(text = it) }
        }
    }
}

const val SAVE_BUTTON_TAG = "detail_save"
const val REMOVE_BUTTON_TAG = "detail_remove"

@ScreenPreviews
@Composable
private fun CandidateDetailPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        CandidateDetailScreen(
            state = previewState(PreviewData.detail, PickState.NotSaved),
            onBack = {},
            onSave = {},
            onRemove = {},
            onConfirmReplace = {},
            onDismissReplace = {},
            onUndoRemove = {},
            onMessageShown = {},
            onRetry = {},
            onOpenBallot = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun CandidateDetailSavedPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        CandidateDetailScreen(
            state = previewState(PreviewData.detail, PickState.SavedHere),
            onBack = null,
            onSave = {},
            onRemove = {},
            onConfirmReplace = {},
            onDismissReplace = {},
            onUndoRemove = {},
            onMessageShown = {},
            onRetry = {},
            onOpenBallot = {},
        )
    }
}

private fun previewState(detail: CandidateDetail, pickState: PickState) = CandidateDetailUiState(
    content = DetailContent.Loaded,
    candidate = detail.candidate,
    detail = detail,
    officeName = "Senador",
    unitName = "São Paulo",
    digitCount = 3,
    numberText = detail.candidate.number.toString(),
    slot = 1,
    maxPicks = 2,
    freshness = PreviewData.freshness(),
    pickState = pickState,
)
