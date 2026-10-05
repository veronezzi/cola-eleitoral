package com.veronezzi.colaeleitoral.ui.screens.location

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.CachedData
import com.veronezzi.colaeleitoral.domain.model.ElectionScope
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.domain.model.normalizeForSearch
import com.veronezzi.colaeleitoral.domain.repository.ElectionRepository
import com.veronezzi.colaeleitoral.domain.repository.SettingsRepository
import com.veronezzi.colaeleitoral.ui.common.AppClock
import com.veronezzi.colaeleitoral.ui.common.ElectionSelection
import com.veronezzi.colaeleitoral.ui.common.LoadState
import com.veronezzi.colaeleitoral.ui.common.UiDispatchers
import com.veronezzi.colaeleitoral.ui.common.loadStateOf
import com.veronezzi.colaeleitoral.ui.common.resolveElection
import com.veronezzi.colaeleitoral.ui.navigation.LocationRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LocationStep { UF, MUNICIPALITY }

data class LocationUiState(
    val step: LocationStep = LocationStep.UF,
    val fromSettings: Boolean = false,
    val currentLocation: VoterLocation? = null,
    val selectedUf: String? = null,
    val municipalities: List<ElectoralUnit> = emptyList(),
    val municipalitiesLoad: LoadState = LoadState.Loading,
    /** The current election is municipal: the município cannot be skipped. */
    val municipalityRequired: Boolean = false,
    val isSaving: Boolean = false,
    val done: Boolean = false,
    /** The place could not be saved (disk full): shown once, the choice can be tried again. */
    val saveError: AppError? = null,
)

private data class MunicipalityRefresh(val isRefreshing: Boolean = false, val error: AppError? = null)

/** Municipalities of a UF with their search keys, normalized and sorted once per download. */
private class MunicipalityIndex(val cached: CachedData<List<ElectoralUnit>>) {
    val entries: List<Pair<String, ElectoralUnit>> = cached.value
        .map { normalizeForSearch(it.name) to it }
        .sortedBy { it.first }

    fun search(normalizedQuery: String): List<ElectoralUnit> =
        if (normalizedQuery.isEmpty()) {
            entries.map { it.second }
        } else {
            entries.mapNotNull { (key, unit) -> unit.takeIf { key.contains(normalizedQuery) } }
        }
}

/**
 * Voting place (ARCHITECTURE.md 4.2): UF (27 + "Exterior"), then the município, which is optional
 * in general elections ("necessário nas eleições municipais"). The DF and voters abroad are never
 * asked for one. The search text is Compose state written synchronously ([query]); the search
 * itself is debounced (250 ms) and runs on [UiDispatchers.default] over keys normalized once per
 * list (853 municipalities in MG), never on the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel(assistedFactory = LocationViewModel.Factory::class)
class LocationViewModel @AssistedInject constructor(
    @Assisted private val route: LocationRoute,
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val electionRepository: ElectionRepository,
    private val electionSelection: ElectionSelection,
    private val clock: AppClock,
    private val dispatchers: UiDispatchers,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: LocationRoute): LocationViewModel
    }

    private val step = savedStateHandle.getStateFlow(KEY_STEP, LocationStep.UF.name)
    private val selectedUf = savedStateHandle.getStateFlow<String?>(KEY_UF, null)
    private val refresh = MutableStateFlow(MunicipalityRefresh())
    private val saving = MutableStateFlow(SaveState())

    /** Text of the search field. Also kept in the [SavedStateHandle] for process death. */
    var query: String by mutableStateOf(savedStateHandle.get<String>(KEY_QUERY).orEmpty())
        private set

    private val normalizedQuery: Flow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")
        .debounce { text -> if (text.isEmpty()) 0L else SEARCH_DEBOUNCE_MILLIS }
        .map { normalizeForSearch(it) }
        .distinctUntilChanged()

    private val index: Flow<MunicipalityIndex?> = selectedUf
        .flatMapLatest { uf ->
            if (uf == null || !asksMunicipality(uf)) flowOf(null) else electionRepository.observeMunicipalities(uf)
        }
        .map { cached -> cached?.let(::MunicipalityIndex) }
        .flowOn(dispatchers.default)

    /** The index with the matches of the current search, or null before a UF with municipalities. */
    private val matches: Flow<Pair<MunicipalityIndex, List<ElectoralUnit>>?> =
        combine(index, normalizedQuery) { index, query -> index?.let { it to it.search(query) } }
            .flowOn(dispatchers.default)

    private val municipalityRequired = combine(
        electionRepository.observeElections(),
        electionSelection.selectedElectionId,
    ) { elections, selected ->
        resolveElection(elections.value, selected, clock.todayInBrasilia())?.election?.scope == ElectionScope.MUNICIPAL
    }

    val uiState: StateFlow<LocationUiState> = combine(
        combine(step, selectedUf) { step, uf -> step to uf },
        matches,
        municipalityRequired,
        combine(refresh, saving, settingsRepository.settings.map { it.location }) { r, s, l -> Triple(r, s, l) },
    ) { (step, uf), matches, required, (refresh, saving, location) ->
        buildState(step, uf, matches, required, refresh, saving, location)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationUiState(fromSettings = route.fromSettings))

    init {
        viewModelScope.launch { electionRepository.refreshElections() }
        selectedUf.value?.let { uf -> if (asksMunicipality(uf)) refreshMunicipalities(uf, force = false) }
    }

    fun onUfSelected(uf: String) {
        if (asksMunicipality(uf)) {
            savedStateHandle[KEY_UF] = uf
            onQueryChange("")
            savedStateHandle[KEY_STEP] = LocationStep.MUNICIPALITY.name
            refreshMunicipalities(uf, force = false)
        } else {
            save(VoterLocation(uf = uf))
        }
    }

    fun onQueryChange(text: String) {
        query = text
        savedStateHandle[KEY_QUERY] = text
    }

    fun onSaveErrorShown() {
        saving.update { it.copy(error = null) }
    }

    fun onMunicipalitySelected(municipality: ElectoralUnit) {
        val uf = selectedUf.value ?: return
        save(VoterLocation(uf = uf, municipality = municipality))
    }

    /** Only when the current election is not municipal. */
    fun onSkipMunicipality() {
        val uf = selectedUf.value ?: return
        save(VoterLocation(uf = uf))
    }

    fun onBackToUf() {
        savedStateHandle[KEY_STEP] = LocationStep.UF.name
    }

    fun onRetry() {
        selectedUf.value?.let { refreshMunicipalities(it, force = true) }
    }

    private fun refreshMunicipalities(uf: String, force: Boolean) {
        viewModelScope.launch {
            refresh.value = MunicipalityRefresh(isRefreshing = true)
            var error: AppError? = null
            try {
                error = (electionRepository.refreshMunicipalities(uf, force) as? AppResult.Failure)?.error
            } finally {
                refresh.value = MunicipalityRefresh(error = error)
            }
        }
    }

    private fun save(location: VoterLocation) {
        if (saving.value.isSaving) return
        saving.value = SaveState(isSaving = true)
        viewModelScope.launch {
            var result: AppResult<Unit> = AppResult.Failure(AppError.Storage)
            try {
                result = settingsRepository.setLocation(location)
            } finally {
                saving.update {
                    it.copy(isSaving = false, done = result is AppResult.Success, error = (result as? AppResult.Failure)?.error)
                }
            }
        }
    }

    private fun buildState(
        stepName: String,
        uf: String?,
        matches: Pair<MunicipalityIndex, List<ElectoralUnit>>?,
        required: Boolean,
        refresh: MunicipalityRefresh,
        saving: SaveState,
        location: VoterLocation?,
    ): LocationUiState {
        val cached = matches?.first?.cached
        return LocationUiState(
            step = LocationStep.valueOf(stepName),
            fromSettings = route.fromSettings,
            currentLocation = location,
            selectedUf = uf,
            municipalities = matches?.second.orEmpty(),
            municipalitiesLoad = loadStateOf(
                hasData = cached?.value?.isNotEmpty() == true,
                error = refresh.error ?: cached?.lastError,
                isRefreshing = refresh.isRefreshing || cached == null,
            ),
            municipalityRequired = required,
            isSaving = saving.isSaving,
            done = saving.done,
            saveError = saving.error,
        )
    }

    private data class SaveState(val isSaving: Boolean = false, val done: Boolean = false, val error: AppError? = null)

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 250L
        private const val KEY_STEP = "step"
        private const val KEY_UF = "uf"
        private const val KEY_QUERY = "query"

        /** The DF has no municipal elections; voters abroad vote only for President. */
        fun asksMunicipality(uf: String): Boolean = uf != "DF" && uf != ElectoralUnit.ABROAD_CODE
    }
}
