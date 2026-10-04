package com.veronezzi.colaeleitoral.ui.screens.ballot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.ui.common.BallotSlot
import com.veronezzi.colaeleitoral.ui.common.SecureScreen
import com.veronezzi.colaeleitoral.ui.common.formatDate
import com.veronezzi.colaeleitoral.ui.common.slotLabel
import com.veronezzi.colaeleitoral.ui.common.slotSpokenLabel
import com.veronezzi.colaeleitoral.ui.common.spokenDigits
import com.veronezzi.colaeleitoral.ui.components.AppTopBar
import com.veronezzi.colaeleitoral.ui.components.DigitBoxSize
import com.veronezzi.colaeleitoral.ui.components.DigitBoxes
import com.veronezzi.colaeleitoral.ui.components.ListSkeleton
import com.veronezzi.colaeleitoral.ui.components.ScreenPreviews
import com.veronezzi.colaeleitoral.ui.components.SectionHeader
import com.veronezzi.colaeleitoral.ui.navigation.BallotRoute
import com.veronezzi.colaeleitoral.ui.preview.PreviewData
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme

/** Navigation out of "Meu santinho". */
data class BallotActions(
    val onChoose: (slot: BallotSlot, electionId: Long, year: Int, round: Round) -> Unit,
    val onOpenPick: (pick: BallotPick) -> Unit,
    val onExport: (electionId: Long, round: Round) -> Unit,
)

@Composable
fun BallotRouteScreen(
    route: BallotRoute,
    actions: BallotActions,
    viewModel: BallotViewModel = hiltViewModel<BallotViewModel, BallotViewModel.Factory> { it.create(route) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BallotScreen(
        state = state,
        actions = actions,
        onRoundSelected = viewModel::onRoundSelected,
        onRemove = viewModel::onRemove,
        onUndoRemove = viewModel::onUndoRemove,
        onClearRequested = viewModel::onClearRequested,
        onClearConfirmed = viewModel::onClearConfirmed,
        onClearDismissed = viewModel::onClearDismissed,
        onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
fun BallotScreen(
    state: BallotUiState,
    actions: BallotActions,
    onRoundSelected: (Round) -> Unit,
    onRemove: (BallotPick) -> Unit,
    onUndoRemove: (BallotPick) -> Unit,
    onClearRequested: () -> Unit,
    onClearConfirmed: () -> Unit,
    onClearDismissed: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Picks are sensitive (political opinion): no screenshots, no recents thumbnail.
    SecureScreen()
    val snackbarHostState = remember { SnackbarHostState() }
    state.message?.let { message ->
        val text = stringResource(if (message is BallotMessage.Removed) R.string.ballot_removed else R.string.ballot_cleared)
        val undo = stringResource(R.string.action_undo)
        LaunchedEffect(message) {
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = if (message is BallotMessage.Removed) undo else null,
            )
            if (result == SnackbarResult.ActionPerformed && message is BallotMessage.Removed) onUndoRemove(message.pick)
            onMessageShown()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(R.string.ballot_title)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            ListSkeleton(
                description = stringResource(R.string.ballot_loading),
                modifier = Modifier.padding(padding),
                rows = 4,
                avatarSize = 32.dp,
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = "header") { BallotHeader(state = state, onRoundSelected = onRoundSelected) }
            items(state.entries, key = { it.slot.key }) { entry ->
                BallotEntryCard(
                    entry = entry,
                    canEdit = state.canEdit,
                    onChoose = { actions.onChoose(entry.slot, state.electionId, state.electionYear, state.round) },
                    onOpenPick = actions.onOpenPick,
                    onRemove = onRemove,
                )
            }
            if (state.outsidePicks.isNotEmpty()) {
                item(key = "outside-header") { SectionHeader(text = stringResource(R.string.ballot_outside_title)) }
                items(state.outsidePicks, key = { "outside:${it.officeCode}:${it.slot}" }) { pick ->
                    OutsidePickRow(pick = pick, onRemove = onRemove)
                }
            }
            item(key = "actions") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { actions.onExport(state.electionId, state.round) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.ballot_export))
                    }
                    if (state.hasPicks) {
                        OutlinedButton(onClick = onClearRequested, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ballot_clear))
                        }
                    }
                }
            }
        }
    }
    if (state.confirmClear) {
        AlertDialog(
            onDismissRequest = onClearDismissed,
            title = { Text(stringResource(R.string.ballot_clear_title)) },
            text = { Text(stringResource(R.string.ballot_clear_message)) },
            confirmButton = { TextButton(onClick = onClearConfirmed) { Text(stringResource(R.string.ballot_clear_confirm)) } },
            dismissButton = { TextButton(onClick = onClearDismissed) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun BallotHeader(state: BallotUiState, onRoundSelected: (Round) -> Unit) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.electionName.isNotBlank()) {
            Text(
                text = state.electionName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        val roundText = stringResource(if (state.round == Round.FIRST) R.string.round_first else R.string.round_second)
        Text(
            text = state.roundDate?.let { stringResource(R.string.home_round_and_date, roundText, it.formatDate()) } ?: roundText,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (state.showRoundSwitch) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Round.entries.forEachIndexed { index, round ->
                    SegmentedButton(
                        selected = state.round == round,
                        onClick = { onRoundSelected(round) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = Round.entries.size),
                    ) {
                        Text(stringResource(if (round == Round.FIRST) R.string.round_first else R.string.round_second))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.ballot_privacy_note), style = MaterialTheme.typography.bodyMedium)
        }
        if (!state.canEdit) {
            Text(
                text = stringResource(R.string.ballot_read_only),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One vote: label, one box per digit and the pick. TalkBack reads the block as a single node,
 * "Senador, primeira vaga: 1 2 3, FULANO, PARTIDO" (ARCHITECTURE.md 4.6); actions stay separate.
 */
@Composable
private fun BallotEntryCard(
    entry: BallotEntry,
    canEdit: Boolean,
    onChoose: () -> Unit,
    onOpenPick: (BallotPick) -> Unit,
    onRemove: (BallotPick) -> Unit,
) {
    val slot = entry.slot
    val pick = slot.pick
    val label = slotLabel(slot.office.name, slot.slot, slot.hasSeveralSeats)
    val spokenLabel = slotSpokenLabel(slot.office.name, slot.slot, slot.hasSeveralSeats)
    val spoken = if (pick != null) {
        stringResource(R.string.ballot_entry_spoken, spokenLabel, spokenDigits(pick.candidateNumber), pick.ballotName, pick.partyAcronym)
    } else {
        stringResource(R.string.ballot_entry_empty_spoken, spokenLabel, slot.office.digitCount)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.ballot_vote_label, slot.voteNumber, label),
                    style = MaterialTheme.typography.titleMedium,
                )
                DigitBoxes(digits = pick?.candidateNumber, digitCount = slot.office.digitCount, size = DigitBoxSize.Medium)
                Text(
                    text = pick?.let { stringResource(R.string.pick_name_and_party, it.ballotName, it.partyAcronym) }
                        ?: stringResource(R.string.home_no_pick),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (pick == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            entry.updatedStatus?.let { status ->
                Notice(icon = Icons.Outlined.Update, text = stringResource(R.string.ballot_status_changed, status))
            }
            if (slot.pickIsFromOtherPlace && pick != null) {
                Notice(icon = Icons.Outlined.WarningAmber, text = stringResource(R.string.ballot_other_place, pick.ueCode))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canEdit) {
                    TextButton(onClick = onChoose) {
                        Text(stringResource(if (pick == null) R.string.home_choose else R.string.home_change_pick))
                    }
                }
                if (pick != null) {
                    TextButton(onClick = { onOpenPick(pick) }) { Text(stringResource(R.string.ballot_see_detail)) }
                    TextButton(onClick = { onRemove(pick) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.ballot_remove))
                    }
                }
            }
        }
    }
}

@Composable
private fun OutsidePickRow(pick: BallotPick, onRemove: (BallotPick) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = pick.officeName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.pick_name_and_party, pick.ballotName, pick.partyAcronym),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.ballot_other_place, pick.ueCode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onRemove(pick) }) { Text(stringResource(R.string.ballot_remove)) }
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@ScreenPreviews
@Composable
private fun BallotScreenPreview() {
    val slots = com.veronezzi.colaeleitoral.ui.common.buildBallotSlots(PreviewData.offices, PreviewData.picks)
    ColaEleitoralTheme(dynamicColor = false) {
        BallotScreen(
            state = BallotUiState(
                isLoading = false,
                electionId = PreviewData.election.id,
                electionYear = 2026,
                electionName = PreviewData.election.name,
                roundDate = PreviewData.election.date,
                entries = slots.map { slot ->
                    BallotEntry(slot, updatedStatus = if (slot.slot == 2 && slot.pick != null) "Deferido" else null)
                },
            ),
            actions = BallotActions({ _, _, _, _ -> }, {}, { _, _ -> }),
            onRoundSelected = {},
            onRemove = {},
            onUndoRemove = {},
            onClearRequested = {},
            onClearConfirmed = {},
            onClearDismissed = {},
            onMessageShown = {},
        )
    }
}
