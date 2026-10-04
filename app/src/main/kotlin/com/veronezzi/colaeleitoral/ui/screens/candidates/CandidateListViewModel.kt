package com.veronezzi.colaeleitoral.ui.screens.candidates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.CachedData
import com.veronezzi.colaeleitoral.domain.model.Candidate
import com.veronezzi.colaeleitoral.domain.model.CandidateFilter
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectionScope
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import com.veronezzi.colaeleitoral.domain.model.FederativeUnits
import com.veronezzi.colaeleitoral.domain.model.FilterOptions
import com.veronezzi.colaeleitoral.domain.model.Office
import com.veronezzi.colaeleitoral.domain.model.OfficeRules
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.SortOrder
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.domain.model.filteredBy
import com.veronezzi.colaeleitoral.domain.repository.BallotRepository
import com.veronezzi.colaeleitoral.domain.repository.CandidateRepository
import com.veronezzi.colaeleitoral.domain.repository.ElectionRepository
import com.veronezzi.colaeleitoral.domain.repository.SettingsRepository
import com.veronezzi.colaeleitoral.ui.common.Freshness
import com.veronezzi.colaeleitoral.ui.common.LoadState
import com.veronezzi.colaeleitoral.ui.common.UiDispatchers
import com.veronezzi.colaeleitoral.ui.common.candidateNumberText
import com.veronezzi.colaeleitoral.ui.common.loadStateOf
import com.veronezzi.colaeleitoral.ui.common.toFreshness
import com.veronezzi.colaeleitoral.ui.navigation.CandidateListRoute
import com.veronezzi.colaeleitoral.ui.navigation.roundOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A row of the list. [isSaved]: the user's pick for this vote; [isInOtherSlot]: the other Senate vote. */
data class CandidateItem(
    val candidate: Candidate,
    val numberText: String,
    val isSaved: Boolean,
    val isInOtherSlot: Boolean,
)

sealed interface ListContent {
    data object Loading : ListContent

    data class Failed(val error: AppError) : ListContent

    /** The TSE lists nobody for this office and unit. */
    data object EmptyFromTse : ListContent

    /** Candidates exist, but none matches the search and filters. */
    data object EmptyForFilters : ListContent

    data object Items : ListContent
}

data class CandidateListUiState(
    val officeCode: Int,
    val officeName: String,
    val unitName: String,
    val slot: Int,
    val maxPicks: Int,
    val isSecondRound: Boolean,
    val query: String = "",
    val filter: CandidateFilter = CandidateFilter(),
    val options: FilterOptions = FilterOptions(emptyList(), emptyList()),
    val offices: List<Office> = emptyList(),
    val items: List<CandidateItem> = emptyList(),
    val totalCount: Int = 0,
    val content: ListContent = ListContent.Loading,
    val freshness: Freshness? = null,
    val isRefreshing: Boolean = false,
    /** Photos only for majoritarian offices (ARCHITECTURE.md 2.9); initials for everybody else. */
    val showPhotos: Boolean = false,
) {
    val hasSeveralSeats: Boolean get() = maxPicks > 1

    val activeFilterCount: Int
        get() = filter.partyAcronyms.size + filter.registrationStatuses.size + if (filter.onlySecondRound) 1 else 0
}

private data class ListRefresh(val isRefreshing: Boolean = false, val error: AppError? = null)

/**
 * Every candidate the TSE lists for one office and unit (ARCHITECTURE.md 4.4). Default order is
 * by number; the other orders are alphabetical. Nothing is ranked, highlighted or recommended:
 * the user's own pick is only marked as such. Search is debounced (250 ms) and filtering runs on
 * [UiDispatchers.default] over the cached list.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel(assistedFactory = CandidateListViewModel.Factory::class)
class CandidateListViewModel @AssistedInject constructor(
    @Assisted private val route: CandidateListRoute,
    private val savedStateHandle: SavedStateHandle,
    private val candidateRepository: CandidateRepository,
    private val electionRepository: ElectionRepository,
    private val settingsRepository: SettingsRepository,
    private val ballotRepository: BallotRepository,
    private val dispatchers: UiDispatchers,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: CandidateListRoute): CandidateListViewModel
    }

    private val round: Round = roundOf(route.round)
    private val refresh = MutableStateFlow(ListRefresh())

    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val parties = savedStateHandle.getStateFlow(KEY_PARTIES, arrayListOf<String>())
    private val statuses = savedStateHandle.getStateFlow(KEY_STATUSES, arrayListOf<String>())
    private val onlyRunoff = savedStateHandle.getStateFlow(KEY_ONLY_RUNOFF, round == Round.SECOND)
    private val sort = savedStateHandle.getStateFlow(KEY_SORT, SortOrder.NUMBER.name)

    private val debouncedQuery: Flow<String> = query
        .debounce { text -> if (text.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS }
        .distinctUntilChanged()

    private val filter: Flow<CandidateFilter> = combine(debouncedQuery, parties, statuses, onlyRunoff, sort) {
            text, parties, statuses, onlyRunoff, sort ->
        CandidateFilter(
            query = text,
            partyAcronyms = parties.toSet(),
            registrationStatuses = statuses.toSet(),
            onlySecondRound = onlyRunoff,
            sortOrder = SortOrder.entries.firstOrNull { it.name == sort } ?: SortOrder.NUMBER,
        )
    }.distinctUntilChanged()

    private val candidates: Flow<CachedData<List<Candidate>>> =
        candidateRepository.observeCandidates(route.electionId, route.ueCode, route.officeCode)

    private val filtered: Flow<Pair<CachedData<List<Candidate>>, List<Candidate>>> =
        combine(candidates, filter) { cached, filter -> cached to cached.value.filteredBy(filter) }
            .flowOn(dispatchers.default)

    private val election: Flow<Election?> = electionRepository.observeElections()
        .map { cached -> cached.value.firstOrNull { it.id == route.electionId } }
        .distinctUntilChanged()

    private val location: Flow<VoterLocation?> = settingsRepository.settings.map { it.location }.distinctUntilChanged()

    private val offices: Flow<List<Office>> = combine(election, location) { election, location -> election to location }
        .flatMapLatest { (election, location) ->
            if (election == null || location == null) {
                flowOf(emptyList())
            } else {
                electionRepository.observeBallotOffices(election, location, round).map { it.value }
            }
        }

    private val picks = ballotRepository.observeBallot(route.electionId, round)

    val uiState: StateFlow<CandidateListUiState> = combine(
        combine(filtered, filter, query) { filtered, filter, query -> Triple(filtered, filter, query) },
        candidateRepository.observeFilterOptions(route.electionId, route.ueCode, route.officeCode),
        offices,
        combine(picks, location) { picks, location -> picks to location },
        refresh,
    ) { (filtered, filter, query), options, offices, (picks, location), refresh ->
        val (cached, list) = filtered
        val office = offices.firstOrNull { it.code == route.officeCode }
        val samePick = picks.firstOrNull { it.officeCode == route.officeCode && it.slot == route.slot }
        val otherSlots = picks.filter { it.officeCode == route.officeCode && it.slot != route.slot }
            .map { it.candidateId }
            .toSet()
        val digitCount = office?.digitCount ?: OfficeRules.digitCount(route.officeCode)
        val load = loadStateOf(
            hasData = cached.fetchedAt != null || cached.value.isNotEmpty(),
            error = refresh.error ?: cached.lastError,
            isRefreshing = refresh.isRefreshing,
        )
        CandidateListUiState(
            officeCode = route.officeCode,
            officeName = office?.name ?: OfficeRules.canonicalName(route.officeCode).orEmpty(),
            unitName = unitName(route.ueCode, location),
            slot = route.slot,
            maxPicks = office?.maxPicks ?: OfficeRules.maxPicks(route.officeCode, route.year, round),
            isSecondRound = round == Round.SECOND,
            query = query,
            filter = filter,
            options = options,
            offices = offices,
            items = list.map { candidate ->
                CandidateItem(
                    candidate = candidate,
                    numberText = candidateNumberText(candidate.number, digitCount),
                    isSaved = samePick?.candidateId == candidate.id,
                    isInOtherSlot = candidate.id in otherSlots,
                )
            },
            totalCount = cached.value.size,
            content = when (load) {
                LoadState.Loading -> ListContent.Loading
                is LoadState.Failed -> ListContent.Failed(load.error)
                LoadState.Loaded -> when {
                    cached.value.isEmpty() -> ListContent.EmptyFromTse
                    list.isEmpty() -> ListContent.EmptyForFilters
                    else -> ListContent.Items
                }
            },
            freshness = cached.toFreshness(),
            isRefreshing = refresh.isRefreshing,
            showPhotos = route.officeCode in MAJORITARIAN_OFFICES,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CandidateListUiState(
            officeCode = route.officeCode,
            officeName = OfficeRules.canonicalName(route.officeCode).orEmpty(),
            unitName = route.ueCode,
            slot = route.slot,
            maxPicks = OfficeRules.maxPicks(route.officeCode, route.year, round),
            isSecondRound = round == Round.SECOND,
            filter = CandidateFilter(onlySecondRound = round == Round.SECOND),
        ),
    )

    init {
        refresh(force = false)
    }

    fun onQueryChange(text: String) {
        savedStateHandle[KEY_QUERY] = text
    }

    fun onPartyToggled(acronym: String) {
        savedStateHandle[KEY_PARTIES] = ArrayList(parties.value.toggle(acronym))
    }

    fun onStatusToggled(status: String) {
        savedStateHandle[KEY_STATUSES] = ArrayList(statuses.value.toggle(status))
    }

    fun onOnlySecondRoundChange(enabled: Boolean) {
        savedStateHandle[KEY_ONLY_RUNOFF] = enabled
    }

    fun onSortChange(order: SortOrder) {
        savedStateHandle[KEY_SORT] = order.name
    }

    /** Clears party, status and runoff filters (search and order stay). */
    fun onClearFilters() {
        savedStateHandle[KEY_PARTIES] = arrayListOf<String>()
        savedStateHandle[KEY_STATUSES] = arrayListOf<String>()
        savedStateHandle[KEY_ONLY_RUNOFF] = false
    }

    fun onRefresh() = refresh(force = true)

    private fun refresh(force: Boolean) {
        viewModelScope.launch {
            refresh.update { ListRefresh(isRefreshing = true) }
            val election = electionRepository.observeElections().first().value
                .firstOrNull { it.id == route.electionId }
                ?: fallbackElection()
            val result = candidateRepository.refreshCandidates(election, route.ueCode, route.officeCode, force)
            refresh.update { ListRefresh(error = (result as? AppResult.Failure)?.error) }
        }
    }

    /** The election as far as the route knows it, when the elections cache is gone. */
    private fun fallbackElection(): Election = Election(
        id = route.electionId,
        year = route.year,
        name = "",
        round = null,
        scope = if (route.ueCode.all { it.isDigit() }) ElectionScope.MUNICIPAL else ElectionScope.GENERAL,
        date = null,
    )

    private fun List<String>.toggle(value: String): List<String> = if (value in this) this - value else this + value

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 250L
        private const val KEY_QUERY = "query"
        private const val KEY_PARTIES = "parties"
        private const val KEY_STATUSES = "statuses"
        private const val KEY_ONLY_RUNOFF = "onlySecondRound"
        private const val KEY_SORT = "sort"

        private val MAJORITARIAN_OFFICES = setOf(
            OfficeRules.PRESIDENT,
            OfficeRules.GOVERNOR,
            OfficeRules.SENATOR,
            OfficeRules.MAYOR,
        )

        /** Name of an electoral unit code: "Brasil", the UF name or the voter's município. */
        fun unitName(ueCode: String, location: VoterLocation?): String {
            if (ueCode == ElectoralUnit.BRAZIL_CODE) return ElectoralUnit.BRAZIL.name
            location?.municipality?.takeIf { it.code == ueCode }?.let { return it.name }
            return FederativeUnits.byCode(ueCode)?.name ?: ueCode
        }
    }
}
