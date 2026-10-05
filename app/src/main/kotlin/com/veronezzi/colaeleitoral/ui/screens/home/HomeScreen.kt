package com.veronezzi.colaeleitoral.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.domain.model.ElectionScope
import com.veronezzi.colaeleitoral.domain.model.FederativeUnits
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.ui.common.BallotSlot
import com.veronezzi.colaeleitoral.ui.common.LoadState
import com.veronezzi.colaeleitoral.ui.common.SecureScreen
import com.veronezzi.colaeleitoral.ui.common.formatDate
import com.veronezzi.colaeleitoral.ui.common.slotLabel
import com.veronezzi.colaeleitoral.ui.components.AppTopBar
import com.veronezzi.colaeleitoral.ui.components.DataFreshnessBar
import com.veronezzi.colaeleitoral.ui.components.DigitBoxSize
import com.veronezzi.colaeleitoral.ui.components.DigitBoxes
import com.veronezzi.colaeleitoral.ui.components.ErrorState
import com.veronezzi.colaeleitoral.ui.components.ListSkeleton
import com.veronezzi.colaeleitoral.ui.components.MessageState
import com.veronezzi.colaeleitoral.ui.components.PicksUnavailableBanner
import com.veronezzi.colaeleitoral.ui.components.ScreenPreviews
import com.veronezzi.colaeleitoral.ui.components.SectionHeader
import com.veronezzi.colaeleitoral.ui.components.rememberReminderOptIn
import com.veronezzi.colaeleitoral.ui.preview.PreviewData
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme

/** Navigation callbacks of the Home. */
data class HomeActions(
    val onOpenSlot: (electionId: Long, year: Int, slot: BallotSlot, round: Round) -> Unit,
    val onOpenBallot: (electionId: Long, round: Round) -> Unit,
    val onOpenSettings: () -> Unit,
    val onChangeLocation: () -> Unit,
)

@Composable
fun HomeRoute(
    actions: HomeActions,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }
    val reminderOptIn = rememberReminderOptIn(onEnabled = viewModel::onReminderEnabled)
    HomeScreen(
        state = state,
        actions = actions,
        onRefresh = viewModel::onRefresh,
        onElectionSelected = viewModel::onElectionSelected,
        onEnableReminder = reminderOptIn::start,
        onRetryRead = viewModel::onRetryRead,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    onRefresh: () -> Unit,
    onElectionSelected: (Long) -> Unit,
    onEnableReminder: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryRead: () -> Unit = {},
) {
    SecureScreen(active = state.showsPicks)
    var showElectionPicker by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                when (val load = state.loadState) {
                    LoadState.Loading -> item(key = "loading") {
                        ListSkeleton(description = stringResource(R.string.home_loading), rows = 5)
                    }
                    is LoadState.Failed -> item(key = "error") {
                        ErrorState(error = load.error, onRetry = onRefresh)
                    }
                    LoadState.Loaded -> homeContent(
                        state = state,
                        actions = actions,
                        onPickElection = { showElectionPicker = true },
                        onEnableReminder = onEnableReminder,
                        onRetryRead = onRetryRead,
                    )
                }
            }
        }
    }
    if (showElectionPicker) {
        ElectionPickerDialog(
            state = state,
            onSelected = { id ->
                showElectionPicker = false
                onElectionSelected(id)
            },
            onDismiss = { showElectionPicker = false },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeContent(
    state: HomeUiState,
    actions: HomeActions,
    onPickElection: () -> Unit,
    onEnableReminder: () -> Unit,
    onRetryRead: () -> Unit,
) {
    val election = state.election ?: return
    item(key = "election") {
        ElectionCard(
            election = election,
            location = state.location,
            onPickElection = onPickElection,
            onChangeLocation = actions.onChangeLocation,
        )
    }
    item(key = "ballot-header") {
        SectionHeader(text = stringResource(R.string.home_ballot_title), modifier = Modifier.padding(top = 8.dp))
    }
    if (state.picksUnavailable) {
        item(key = "picks-unavailable") { PicksUnavailableBanner(onRetry = onRetryRead) }
    }
    when (val ballot = state.ballot) {
        BallotSection.Loading -> item(key = "ballot-loading") {
            ListSkeleton(description = stringResource(R.string.home_loading_ballot), rows = 3, avatarSize = 40.dp)
        }
        is BallotSection.Unavailable -> item(key = "ballot-unavailable") {
            NoBallotMessage(reason = ballot.reason, onChangeLocation = actions.onChangeLocation)
        }
        BallotSection.NoRunoffHere -> item(key = "no-runoff") {
            MessageState(
                icon = Icons.Outlined.HowToVote,
                title = stringResource(R.string.home_no_runoff_title),
                message = stringResource(R.string.home_no_runoff_message),
            ) {
                OutlinedButton(onClick = { actions.onOpenBallot(election.id, Round.FIRST) }) {
                    Text(stringResource(R.string.home_see_first_round))
                }
            }
        }
        is BallotSection.Slots -> {
            items(ballot.slots, key = { it.key }) { slot ->
                BallotSlotRow(
                    slot = slot,
                    enabled = election.isRoundOpen,
                    onClick = { actions.onOpenSlot(election.id, election.year, slot, election.round) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item(key = "see-ballot") {
                Button(
                    onClick = { actions.onOpenBallot(election.id, election.round) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.home_see_ballot))
                }
            }
        }
    }
    if (state.offerReminder) {
        item(key = "reminder") { ReminderCard(onEnable = onEnableReminder) }
    }
    item(key = "booth-notice") { BoothNoticeCard() }
    state.freshness?.let { freshness ->
        item(key = "freshness") { DataFreshnessBar(freshness = freshness) }
    }
}

@Composable
private fun ElectionCard(
    election: ElectionSummary,
    location: VoterLocation?,
    onPickElection: () -> Unit,
    onChangeLocation: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = election.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            val roundText = stringResource(
                if (election.round == Round.FIRST) R.string.round_first else R.string.round_second,
            )
            Text(
                text = election.roundDate?.let { stringResource(R.string.home_round_and_date, roundText, it.formatDate()) }
                    ?: roundText,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(text = countdownText(election), style = MaterialTheme.typography.bodyLarge)
            location?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.home_you_vote_in, locationLabel(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPickElection) { Text(stringResource(R.string.home_change_election)) }
                TextButton(onClick = onChangeLocation) { Text(stringResource(R.string.home_change_location)) }
            }
        }
    }
}

@Composable
private fun countdownText(election: ElectionSummary): String {
    val days = election.daysUntil ?: return stringResource(R.string.home_date_unknown)
    return when {
        days > 0 -> pluralStringResource(R.plurals.home_days_left, days.toInt(), days.toInt())
        days == 0L -> stringResource(R.string.home_voting_day)
        else -> stringResource(R.string.home_round_over)
    }
}

/** "SP · São Paulo", "Exterior" or the municipality name with its UF. */
@Composable
fun locationLabel(location: VoterLocation): String = when {
    location.isAbroad -> stringResource(R.string.location_abroad_short)
    location.municipality != null -> stringResource(
        R.string.location_municipality_label,
        location.municipality.name,
        location.uf,
    )
    else -> FederativeUnits.byCode(location.uf)?.name ?: location.uf
}

@Composable
private fun BallotSlotRow(slot: BallotSlot, enabled: Boolean, onClick: () -> Unit) {
    val pick = slot.pick
    val label = slotLabel(slot.office.name, slot.slot, slot.hasSeveralSeats)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = stringResource(if (pick == null) R.string.home_choose else R.string.home_change_pick),
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.ballot_vote_label, slot.voteNumber, label),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = pluralStringResource(R.plurals.digit_count, slot.office.digitCount, slot.office.digitCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DigitBoxes(digits = pick?.candidateNumber, digitCount = slot.office.digitCount, size = DigitBoxSize.Small)
            if (pick != null) {
                Text(
                    text = stringResource(R.string.pick_name_and_party, pick.ballotName, pick.partyAcronym),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(R.string.home_no_pick),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (enabled) {
            Text(
                text = stringResource(if (pick == null) R.string.home_choose else R.string.home_change_pick),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun NoBallotMessage(reason: NoBallotReason, onChangeLocation: () -> Unit) {
    val (title, message) = when (reason) {
        NoBallotReason.NO_LOCATION -> R.string.home_no_location_title to R.string.home_no_location_message
        NoBallotReason.NEEDS_MUNICIPALITY -> R.string.home_needs_municipality_title to R.string.home_needs_municipality_message
        NoBallotReason.NO_MUNICIPAL_ELECTION_IN_DF -> R.string.home_no_municipal_df_title to R.string.home_no_municipal_df_message
        NoBallotReason.NO_MUNICIPAL_ELECTION_ABROAD ->
            R.string.home_no_municipal_abroad_title to R.string.home_no_municipal_abroad_message
    }
    MessageState(
        icon = Icons.Outlined.Place,
        title = stringResource(title),
        message = stringResource(message),
    ) {
        if (reason == NoBallotReason.NO_LOCATION || reason == NoBallotReason.NEEDS_MUNICIPALITY) {
            Button(onClick = onChangeLocation) { Text(stringResource(R.string.home_change_location)) }
        }
    }
}

@Composable
private fun ReminderCard(onEnable: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_reminder_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.home_reminder_message), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onEnable) { Text(stringResource(R.string.home_reminder_action)) }
            }
        }
    }
}

/** Fixed notice: the phone does not enter the booth (Lei 9.504/97, art. 91-A). */
@Composable
private fun BoothNoticeCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Gavel, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text = stringResource(R.string.home_booth_notice), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ElectionPickerDialog(state: HomeUiState, onSelected: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_change_election)) },
        text = {
            LazyColumn(modifier = Modifier.selectableGroup()) {
                items(state.electionOptions, key = { it.id }) { option ->
                    val selected = option.id == state.election?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelected(option.id) })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(text = option.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(max = 480.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@ScreenPreviews
@Composable
private fun HomeScreenPreview() {
    val slots = com.veronezzi.colaeleitoral.ui.common.buildBallotSlots(PreviewData.offices, PreviewData.picks)
    ColaEleitoralTheme(dynamicColor = false) {
        HomeScreen(
            state = HomeUiState(
                loadState = LoadState.Loaded,
                election = ElectionSummary(
                    id = PreviewData.election.id,
                    name = PreviewData.election.name,
                    year = 2026,
                    scope = ElectionScope.GENERAL,
                    round = Round.FIRST,
                    roundDate = PreviewData.election.date,
                    daysUntil = 3,
                    isRoundOpen = true,
                ),
                location = VoterLocation("SP"),
                ballot = BallotSection.Slots(slots),
                freshness = PreviewData.freshness(),
            ),
            actions = HomeActions({ _, _, _, _ -> }, { _, _ -> }, {}, {}),
            onRefresh = {},
            onElectionSelected = {},
            onEnableReminder = {},
        )
    }
}
