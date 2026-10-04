package com.veronezzi.meusantinho.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veronezzi.meusantinho.domain.model.AppError
import com.veronezzi.meusantinho.domain.model.AppResult
import com.veronezzi.meusantinho.domain.model.BallotPick
import com.veronezzi.meusantinho.domain.model.CachedData
import com.veronezzi.meusantinho.domain.model.CandidateFilter
import com.veronezzi.meusantinho.domain.model.Election
import com.veronezzi.meusantinho.domain.model.ElectionScope
import com.veronezzi.meusantinho.domain.model.Office
import com.veronezzi.meusantinho.domain.model.Round
import com.veronezzi.meusantinho.domain.model.UserSettings
import com.veronezzi.meusantinho.domain.model.VoterLocation
import com.veronezzi.meusantinho.domain.repository.BallotRepository
import com.veronezzi.meusantinho.domain.repository.CandidateRepository
import com.veronezzi.meusantinho.domain.repository.ElectionRepository
import com.veronezzi.meusantinho.domain.repository.ReminderScheduler
import com.veronezzi.meusantinho.domain.repository.SettingsRepository
import com.veronezzi.meusantinho.ui.common.AppClock
import com.veronezzi.meusantinho.ui.common.BallotSlot
import com.veronezzi.meusantinho.ui.common.ElectionContext
import com.veronezzi.meusantinho.ui.common.ElectionSelection
import com.veronezzi.meusantinho.ui.common.Freshness
import com.veronezzi.meusantinho.ui.common.LoadState
import com.veronezzi.meusantinho.ui.common.buildBallotSlots
import com.veronezzi.meusantinho.ui.common.isRoundOpen
import com.veronezzi.meusantinho.ui.common.loadStateOf
import com.veronezzi.meusantinho.ui.common.resolveElection
import com.veronezzi.meusantinho.ui.common.toFreshness
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Election card of the Home. [daysUntil] is negative once the round's date has passed. */
data class ElectionSummary(
    val id: Long,
    val name: String,
    val year: Int,
    val scope: ElectionScope,
    val round: Round,
    val roundDate: LocalDate?,
    val daysUntil: Long?,
    val isRoundOpen: Boolean,
)

data class ElectionOption(
    val id: Long,
    val name: String,
    val year: Int,
)

/** Why the Home cannot show a ballot for the voter's place. */
enum class NoBallotReason {
    NO_LOCATION,
    NEEDS_MUNICIPALITY,
    NO_MUNICIPAL_ELECTION_IN_DF,
    NO_MUNICIPAL_ELECTION_ABROAD,
}

sealed interface BallotSection {
    data object Loading : BallotSection

    data class Unavailable(val reason: NoBallotReason) : BallotSection

    /** Second round, and the TSE marks no candidate of the voter's units as "2º turno". */
    data object NoRunoffHere : BallotSection

    data class Slots(val slots: List<BallotSlot>) : BallotSection
}

data class HomeUiState(
    val loadState: LoadState = LoadState.Loading,
    val election: ElectionSummary? = null,
    val electionOptions: List<ElectionOption> = emptyList(),
    val location: VoterLocation? = null,
    val ballot: BallotSection = BallotSection.Loading,
    val freshness: Freshness? = null,
    val isRefreshing: Boolean = false,
    val reminderEnabled: Boolean = false,
) {
    /** Picks are on screen: the window must be protected (ARCHITECTURE.md 5.6). */
    val showsPicks: Boolean
        get() = (ballot as? BallotSection.Slots)?.slots?.any { it.pick != null } == true

    /** Offer the reminder while the vote is at least a day away. */
    val offerReminder: Boolean
        get() = !reminderEnabled && (election?.daysUntil ?: -1) >= 1
}

private data class RefreshState(
    val isRefreshing: Boolean = false,
    val electionsError: AppError? = null,
)

private data class HomeContext(
    val settings: UserSettings,
    val elections: CachedData<List<Election>>,
    val election: ElectionContext?,
    val today: LocalDate,
)

/** Whether the voter's unit has a runoff for an office, once its list was downloaded. */
private data class RunoffInfo(val known: Boolean, val hasCandidates: Boolean)

/**
 * Home: the election card, the voter's ballot in urna order with the saved picks, and the
 * reminder opt-in. Reads the cache first and refreshes in the background; on open it also
 * pre-loads, in sequence, the candidate lists of the ballot (at most 7) so the app works offline
 * on election day (ARCHITECTURE.md 2.8, item 5). The repositories skip downloads within the TTL.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val electionRepository: ElectionRepository,
    private val candidateRepository: CandidateRepository,
    private val ballotRepository: BallotRepository,
    private val reminderScheduler: ReminderScheduler,
    private val electionSelection: ElectionSelection,
    private val clock: AppClock,
) : ViewModel() {
    private val refreshState = MutableStateFlow(RefreshState())
    private val today = MutableStateFlow(clock.todayInBrasilia())

    private val context: StateFlow<HomeContext?> = combine(
        settingsRepository.settings,
        electionRepository.observeElections(),
        electionSelection.selectedElectionId,
        today,
    ) { settings, elections, selectedId, day ->
        HomeContext(
            settings = settings,
            elections = elections,
            election = resolveElection(elections.value, selectedId, day),
            today = day,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** (election, place) pairs: the ballot changes only when one of them does. */
    private val ballotKey: Flow<Pair<ElectionContext, VoterLocation>?> = context
        .map { ctx ->
            val election = ctx?.election
            val location = ctx?.settings?.location
            if (election != null && location != null) election to location else null
        }
        .distinctUntilChanged()

    private val offices: Flow<CachedData<List<Office>>?> = ballotKey.flatMapLatest { key ->
        if (key == null) {
            flowOf(null)
        } else {
            electionRepository.observeBallotOffices(key.first.election, key.second, key.first.round)
        }
    }

    private val picks: Flow<List<BallotPick>> = context
        .map { it?.election }
        .distinctUntilChanged()
        .flatMapLatest { election ->
            if (election == null) flowOf(emptyList()) else ballotRepository.observeBallot(election.election.id, election.round)
        }

    /** Second round only: which offices have candidates marked "2º turno" in the voter's units. */
    private val runoff: Flow<Map<Int, RunoffInfo>> = combine(ballotKey, offices) { key, offices -> key to offices }
        .flatMapLatest { (key, offices) ->
            val election = key?.first
            val officeList = offices?.value.orEmpty()
            if (election == null || election.round != Round.SECOND || officeList.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    officeList.map { office ->
                        candidateRepository
                            .observeCandidates(
                                electionId = election.election.id,
                                ueCode = office.ueCode,
                                officeCode = office.code,
                                filter = CandidateFilter(onlySecondRound = true),
                            )
                            .map { cached ->
                                office.code to RunoffInfo(
                                    known = cached.fetchedAt != null,
                                    hasCandidates = cached.value.isNotEmpty(),
                                )
                            }
                    },
                ) { entries -> entries.toMap() }
            }
        }

    val uiState: StateFlow<HomeUiState> = combine(
        context,
        offices,
        picks,
        runoff,
        refreshState,
    ) { ctx, offices, picks, runoff, refresh ->
        buildState(ctx, offices, picks, runoff, refresh)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    init {
        // Ballot offices and the candidate lists of the ballot, whenever election or place change.
        viewModelScope.launch {
            ballotKey.collectLatest { key ->
                if (key != null) prefetchBallot(key.first, key.second)
            }
        }
        // Opt-in reminder: (re)schedule the voter's upcoming rounds. REPLACE makes it idempotent.
        viewModelScope.launch {
            combine(context, runoff) { ctx, runoff ->
                val election = ctx?.election?.election
                if (ctx == null || election == null || !ctx.settings.reminderEnabled) {
                    null
                } else {
                    ReminderPlan(election, runoff.values.any { it.known && it.hasCandidates }, ctx.today)
                }
            }.distinctUntilChanged().collect { plan -> plan?.let { schedule(it) } }
        }
    }

    /** Called when the screen resumes: refresh within the TTL and pick up a new day. */
    fun onScreenResumed() {
        today.value = clock.todayInBrasilia()
        viewModelScope.launch { refresh(force = false) }
    }

    /** Pull to refresh or "Tentar de novo". */
    fun onRefresh() {
        viewModelScope.launch {
            refreshState.update { it.copy(isRefreshing = true) }
            refresh(force = true)
            refreshState.update { it.copy(isRefreshing = false) }
        }
    }

    fun onElectionSelected(electionId: Long) {
        electionSelection.select(electionId)
    }

    /** The user accepted the reminder (and the notification permission); scheduling follows. */
    fun onReminderEnabled() {
        viewModelScope.launch { settingsRepository.setReminderEnabled(true) }
    }

    private suspend fun refresh(force: Boolean) {
        val electionsResult = electionRepository.refreshElections(force)
        refreshState.update { it.copy(electionsError = (electionsResult as? AppResult.Failure)?.error) }
        val ctx = context.value
        val election = ctx?.election
        val location = ctx?.settings?.location
        if (election != null && location != null) {
            electionRepository.refreshBallotOffices(election.election, location, force)
        }
    }

    private suspend fun prefetchBallot(election: ElectionContext, location: VoterLocation) {
        electionRepository.refreshBallotOffices(election.election, location)
        val ballotOffices = electionRepository.observeBallotOffices(election.election, location, election.round).first()
        for (office in ballotOffices.value.take(MAX_PREFETCHED_LISTS)) {
            candidateRepository.refreshCandidates(election.election, office.ueCode, office.code)
        }
    }

    private suspend fun schedule(plan: ReminderPlan) {
        val firstRound = plan.election.dateOf(Round.FIRST)
        if (firstRound != null && !firstRound.isBefore(plan.today)) {
            reminderScheduler.schedule(plan.election, Round.FIRST, firstRound)
        }
        val secondRound = plan.election.dateOf(Round.SECOND)
        if (plan.hasRunoff && secondRound != null && !secondRound.isBefore(plan.today)) {
            reminderScheduler.schedule(plan.election, Round.SECOND, secondRound)
        }
    }

    private fun buildState(
        ctx: HomeContext?,
        offices: CachedData<List<Office>>?,
        picks: List<BallotPick>,
        runoff: Map<Int, RunoffInfo>,
        refresh: RefreshState,
    ): HomeUiState {
        if (ctx == null) return HomeUiState(isRefreshing = refresh.isRefreshing)
        val elections = ctx.elections
        val electionContext = ctx.election
        val location = ctx.settings.location
        return HomeUiState(
            loadState = loadStateOf(
                hasData = elections.value.isNotEmpty(),
                error = refresh.electionsError ?: elections.lastError,
                isRefreshing = refresh.isRefreshing,
            ),
            election = electionContext?.let { summaryOf(it, ctx.today) },
            electionOptions = elections.value.map { ElectionOption(it.id, it.name, it.year) },
            location = location,
            ballot = ballotSection(electionContext, location, offices, picks, runoff),
            freshness = (offices ?: elections).toFreshness(),
            isRefreshing = refresh.isRefreshing,
            reminderEnabled = ctx.settings.reminderEnabled,
        )
    }

    private fun ballotSection(
        election: ElectionContext?,
        location: VoterLocation?,
        offices: CachedData<List<Office>>?,
        picks: List<BallotPick>,
        runoff: Map<Int, RunoffInfo>,
    ): BallotSection {
        val municipal = election?.election?.scope == ElectionScope.MUNICIPAL
        return when {
            election == null -> BallotSection.Loading
            location == null -> BallotSection.Unavailable(NoBallotReason.NO_LOCATION)
            municipal && location.isAbroad -> BallotSection.Unavailable(NoBallotReason.NO_MUNICIPAL_ELECTION_ABROAD)
            municipal && location.uf == DISTRITO_FEDERAL ->
                BallotSection.Unavailable(NoBallotReason.NO_MUNICIPAL_ELECTION_IN_DF)
            municipal && location.municipality == null -> BallotSection.Unavailable(NoBallotReason.NEEDS_MUNICIPALITY)
            offices == null || offices.value.isEmpty() -> BallotSection.Loading
            else -> {
                val visible = if (election.round == Round.SECOND) {
                    offices.value.filter { office -> runoff[office.code]?.let { !it.known || it.hasCandidates } ?: true }
                } else {
                    offices.value
                }
                if (visible.isEmpty()) BallotSection.NoRunoffHere else BallotSection.Slots(buildBallotSlots(visible, picks))
            }
        }
    }

    private fun summaryOf(context: ElectionContext, today: LocalDate): ElectionSummary {
        val election = context.election
        val date = election.dateOf(context.round)
        return ElectionSummary(
            id = election.id,
            name = election.name,
            year = election.year,
            scope = election.scope,
            round = context.round,
            roundDate = date,
            daysUntil = date?.let { ChronoUnit.DAYS.between(today, it) },
            isRoundOpen = election.isRoundOpen(context.round, today),
        )
    }

    private data class ReminderPlan(val election: Election, val hasRunoff: Boolean, val today: LocalDate)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** A general-election ballot has 5 offices; the cap only guards against odd TSE data. */
        const val MAX_PREFETCHED_LISTS = 7
        const val DISTRITO_FEDERAL = "DF"
    }
}
