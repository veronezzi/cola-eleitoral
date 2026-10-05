package com.veronezzi.colaeleitoral.ui.screens.ballot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.domain.model.CachedData
import com.veronezzi.colaeleitoral.domain.model.DataSource
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.Office
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.SavePickResult
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.domain.model.normalizeForSearch
import com.veronezzi.colaeleitoral.domain.repository.BallotRepository
import com.veronezzi.colaeleitoral.domain.repository.CandidateRepository
import com.veronezzi.colaeleitoral.domain.repository.ElectionRepository
import com.veronezzi.colaeleitoral.domain.repository.SettingsRepository
import com.veronezzi.colaeleitoral.ui.common.AppClock
import com.veronezzi.colaeleitoral.ui.common.BallotSlot
import com.veronezzi.colaeleitoral.ui.common.buildBallotSlots
import com.veronezzi.colaeleitoral.ui.common.isRoundOpen
import com.veronezzi.colaeleitoral.ui.common.picksOutsideBallot
import com.veronezzi.colaeleitoral.ui.common.tryLocalWrite
import com.veronezzi.colaeleitoral.ui.navigation.BallotRoute
import com.veronezzi.colaeleitoral.ui.navigation.roundOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * A vote of "Minha cola". [updatedStatus] is the TSE registration status found in the cache when
 * it differs from the one saved with the pick (shown verbatim, ARCHITECTURE.md 4.6).
 */
data class BallotEntry(
    val slot: BallotSlot,
    val updatedStatus: String? = null,
)

sealed interface BallotMessage {
    data class Removed(val pick: BallotPick) : BallotMessage

    data object Cleared : BallotMessage

    /** A removal, undo or clearing could not be written (disk full, I/O): nothing changed. */
    data object ChangeFailed : BallotMessage
}

data class BallotUiState(
    val isLoading: Boolean = true,
    val electionId: Long = 0,
    val electionYear: Int = 0,
    val electionName: String = "",
    val round: Round = Round.FIRST,
    val roundDate: LocalDate? = null,
    val entries: List<BallotEntry> = emptyList(),
    /** Picks saved for another place, kept until the user removes them. */
    val outsidePicks: List<BallotPick> = emptyList(),
    val canEdit: Boolean = true,
    val showRoundSwitch: Boolean = false,
    val confirmClear: Boolean = false,
    val message: BallotMessage? = null,
    /** Saved picks can't be read right now (nothing was deleted): banner with retry. */
    val picksUnavailable: Boolean = false,
) {
    val hasPicks: Boolean get() = entries.any { it.slot.pick != null } || outsidePicks.isNotEmpty()
}

private data class BallotTransient(val confirmClear: Boolean = false, val message: BallotMessage? = null)

/** The TSE status of a picked candidate now, and the TSE system it came from. */
private data class CurrentStatus(val text: String, val source: DataSource)

/**
 * "Minha cola" (ARCHITECTURE.md 4.6): the picks of one election round in urna order, read from
 * the encrypted snapshot, so it works with no network and even with the public cache cleared.
 * Whenever the screen resumes, the candidate lists of the picked offices are refreshed within the
 * TTL to catch status changes (judgments go on until the eve of the election). A status change is
 * only reported when both texts come from the same TSE system, compared without case or accents:
 * open data writes "DEFERIDO" where the API writes "Deferido".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = BallotViewModel.Factory::class)
class BallotViewModel @AssistedInject constructor(
    @Assisted private val route: BallotRoute,
    private val savedStateHandle: SavedStateHandle,
    private val electionRepository: ElectionRepository,
    private val ballotRepository: BallotRepository,
    private val candidateRepository: CandidateRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: AppClock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: BallotRoute): BallotViewModel
    }

    private val roundNumber = savedStateHandle.getStateFlow(KEY_ROUND, route.round)
    private val transient = MutableStateFlow(BallotTransient())
    private var refreshJob: Job? = null

    private val election: Flow<Election?> = electionRepository.observeElections()
        .map { cached -> cached.value.firstOrNull { it.id == route.electionId } }
        .distinctUntilChanged()

    private val location: Flow<VoterLocation?> = settingsRepository.settings.map { it.location }.distinctUntilChanged()

    private val offices: Flow<CachedData<List<Office>>?> = combine(election, location, roundNumber) { e, l, r -> Triple(e, l, r) }
        .flatMapLatest { (election, location, round) ->
            if (election == null || location == null) {
                flowOf(null)
            } else {
                electionRepository.observeBallotOffices(election, location, roundOf(round))
            }
        }

    private val picks: Flow<List<BallotPick>> = roundNumber.flatMapLatest { round ->
        ballotRepository.observeBallot(route.electionId, roundOf(round))
    }

    /** Current TSE status per picked candidate id, from the detail cache or else the list cache. */
    private val currentStatuses: Flow<Map<Long, CurrentStatus>> = picks.flatMapLatest { picks ->
        if (picks.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                picks.map { pick ->
                    combine(
                        candidateRepository.observeCandidateDetail(route.electionId, pick.candidateId),
                        candidateRepository.observeCandidates(route.electionId, pick.ueCode, pick.officeCode),
                    ) { detail, list ->
                        val fromDetail = detail.value?.candidate?.status?.registration?.let { CurrentStatus(it, detail.source) }
                        val status = fromDetail
                            ?: list.value.firstOrNull { it.id == pick.candidateId }?.status?.registration
                                ?.let { CurrentStatus(it, list.source) }
                        pick.candidateId to status
                    }
                },
            ) { pairs -> pairs.mapNotNull { (id, status) -> status?.let { id to it } }.toMap() }
        }
    }

    val uiState: StateFlow<BallotUiState> = combine(
        combine(election, roundNumber) { e, r -> e to r },
        offices,
        picks,
        currentStatuses,
        combine(transient, ballotRepository.observeUnavailable()) { t, unavailable -> t to unavailable },
    ) { (election, roundNumber), offices, picks, statuses, (transient, unavailable) ->
        val round = roundOf(roundNumber)
        val today = clock.todayInBrasilia()
        val slots = if (offices != null && offices.value.isNotEmpty()) {
            buildBallotSlots(offices.value, picks)
        } else {
            slotsFromSnapshots(picks)
        }
        val outside = picksOutsideBallot(slots, picks)
        BallotUiState(
            isLoading = election == null && picks.isEmpty() && offices == null,
            electionId = route.electionId,
            electionYear = election?.year ?: picks.firstOrNull()?.electionYear ?: 0,
            electionName = election?.name.orEmpty(),
            round = round,
            roundDate = election?.dateOf(round),
            entries = slots.map { slot ->
                val pick = slot.pick
                BallotEntry(slot = slot, updatedStatus = pick?.let { updatedStatusOf(it, statuses[it.candidateId]) })
            },
            outsidePicks = outside,
            canEdit = election?.isRoundOpen(round, today) ?: true,
            showRoundSwitch = election?.secondRoundDate?.let { second ->
                val first = election.date
                first != null && today.isAfter(first) && !today.isAfter(second.plusDays(RUNOFF_SWITCH_GRACE_DAYS))
            } ?: false,
            confirmClear = transient.confirmClear,
            message = transient.message,
            picksUnavailable = unavailable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BallotUiState(round = roundOf(route.round)))

    init {
        refreshPickedLists()
    }

    /** The screen came back to the foreground: catch status changes published meanwhile. */
    fun onScreenResumed() = refreshPickedLists()

    fun onRoundSelected(round: Round) {
        savedStateHandle[KEY_ROUND] = round.number
    }

    fun onRemove(pick: BallotPick) {
        viewModelScope.launch {
            val removed = tryLocalWrite { ballotRepository.removePick(pick.key) } != null
            transient.update { it.copy(message = if (removed) BallotMessage.Removed(pick) else BallotMessage.ChangeFailed) }
        }
    }

    fun onUndoRemove(pick: BallotPick) {
        viewModelScope.launch {
            val result = tryLocalWrite { ballotRepository.savePick(pick) }
            if (result == null || result is SavePickResult.Failed) {
                transient.update { it.copy(message = BallotMessage.ChangeFailed) }
            }
        }
    }

    /** "Tentar de novo" on the banner shown while the picks can't be read. */
    fun onRetryRead() {
        viewModelScope.launch { tryLocalWrite { ballotRepository.retryRead() } }
    }

    fun onClearRequested() {
        transient.update { it.copy(confirmClear = true) }
    }

    fun onClearDismissed() {
        transient.update { it.copy(confirmClear = false) }
    }

    fun onClearConfirmed() {
        transient.update { it.copy(confirmClear = false) }
        viewModelScope.launch {
            val cleared = tryLocalWrite { ballotRepository.clearBallot(route.electionId, roundOf(roundNumber.value)) } != null
            transient.update { it.copy(message = if (cleared) BallotMessage.Cleared else BallotMessage.ChangeFailed) }
        }
    }

    fun onMessageShown() {
        transient.update { it.copy(message = null) }
    }

    /** Within the TTL the repository skips the download, so resuming often costs nothing. */
    private fun refreshPickedLists() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val election = election.first() ?: return@launch
            val picked = picks.first().map { it.ueCode to it.officeCode }.distinct()
            for ((ueCode, officeCode) in picked) {
                candidateRepository.refreshCandidates(election, ueCode, officeCode)
            }
        }
    }

    /**
     * The status to report as changed, verbatim, or null. Texts from different TSE systems are
     * never compared (open data and the API differ in case and sometimes in wording).
     */
    private fun updatedStatusOf(pick: BallotPick, current: CurrentStatus?): String? = current
        ?.takeIf { it.source == pick.source }
        ?.takeIf { normalizeForSearch(it.text) != normalizeForSearch(pick.statusAtSave) }
        ?.text

    /** The ballot rebuilt from the snapshots alone, when the offices cannot be loaded. */
    private fun slotsFromSnapshots(picks: List<BallotPick>): List<BallotSlot> =
        picks.sortedWith(compareBy({ it.urnaOrder }, { it.slot })).mapIndexed { index, pick ->
            val seats = picks.filter { it.officeCode == pick.officeCode }.maxOf { it.slot }
            BallotSlot(
                office = Office(
                    code = pick.officeCode,
                    name = pick.officeName,
                    digitCount = pick.digitCount,
                    urnaOrder = pick.urnaOrder,
                    maxPicks = seats,
                    ueCode = pick.ueCode,
                ),
                slot = pick.slot,
                voteNumber = index + 1,
                pick = pick,
            )
        }

    private companion object {
        const val KEY_ROUND = "round"

        /** Keeps the round switch for a few days after the runoff, to look back at both ballots. */
        const val RUNOFF_SWITCH_GRACE_DAYS = 7L
    }
}
