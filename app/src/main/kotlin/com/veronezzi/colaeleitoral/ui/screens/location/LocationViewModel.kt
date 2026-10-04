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
import com.veronezzi.colaeleitoral.ui.common.loadStateOf
import com.veronezzi.colaeleitoral.ui.common.resolveElection
import com.veronezzi.colaeleitoral.ui.navigation.LocationRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    val query: String = "",
    val municipalities: List<ElectoralUnit> = emptyList(),
    val municipalitiesLoad: LoadState = LoadState.Loading,
    /** The current election is municipal: the município cannot be skipped. */
    val municipalityRequired: Boolean = false,
    val isSaving: Boolean = false,
    val done: Boolean = false,
)

private data class MunicipalityRefresh(val isRefreshing: Boolean = false, val error: AppError? = null)

/**
 * Voting place (ARCHITECTURE.md 4.2): UF (27 + "Exterior"), then the município, which is optional
 * in general elections ("necessário nas eleições municipais"). The DF and voters abroad are never
 * asked for one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = LocationViewModel.Factory::class)
class LocationViewModel @AssistedInject constructor(
    @Assisted private val route: LocationRoute,
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val electionRepository: ElectionRepository,
    private val electionSelection: ElectionSelection,
    private val clock: AppClock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: LocationRoute): LocationViewModel
    }

    private val step = savedStateHandle.getStateFlow(KEY_STEP, LocationStep.UF.name)
    private val selectedUf = savedStateHandle.getStateFlow<String?>(KEY_UF, null)
    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val refresh = MutableStateFlow(MunicipalityRefresh())
    private val saving = MutableStateFlow(SaveState())

    private val municipalities = selectedUf.flatMapLatest { uf ->
        if (uf == null || !asksMunicipality(uf)) flowOf(null) else electionRepository.observeMunicipalities(uf)
    }

    private val municipalityRequired = combine(
        electionRepository.observeElections(),
        electionSelection.selectedElectionId,
    ) { elections, selected ->
        resolveElection(elections.value, selected, clock.todayInBrasilia())?.election?.scope == ElectionScope.MUNICIPAL
    }

    val uiState: StateFlow<LocationUiState> = combine(
        combine(step, selectedUf, query) { step, uf, query -> Triple(step, uf, query) },
        municipalities,
        municipalityRequired,
        combine(refresh, saving, settingsRepository.settings.map { it.location }) { r, s, l -> Triple(r, s, l) },
    ) { (step, uf, query), cached, required, (refresh, saving, location) ->
        buildState(step, uf, query, cached, required, refresh, saving, location)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationUiState(fromSettings = route.fromSettings))

    init {
        viewModelScope.launch { electionRepository.refreshElections() }
        selectedUf.value?.let { uf -> if (asksMunicipality(uf)) refreshMunicipalities(uf, force = false) }
    }

    fun onUfSelected(uf: String) {
        if (asksMunicipality(uf)) {
            savedStateHandle[KEY_UF] = uf
            savedStateHandle[KEY_QUERY] = ""
            savedStateHandle[KEY_STEP] = LocationStep.MUNICIPALITY.name
            refreshMunicipalities(uf, force = false)
        } else {
            save(VoterLocation(uf = uf))
        }
    }

    fun onQueryChange(text: String) {
        savedStateHandle[KEY_QUERY] = text
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
            val result = electionRepository.refreshMunicipalities(uf, force)
            refresh.value = MunicipalityRefresh(error = (result as? AppResult.Failure)?.error)
        }
    }

    private fun save(location: VoterLocation) {
        if (saving.value.isSaving) return
        saving.value = SaveState(isSaving = true)
        viewModelScope.launch {
            settingsRepository.setLocation(location)
            saving.update { it.copy(isSaving = false, done = true) }
        }
    }

    private fun buildState(
        stepName: String,
        uf: String?,
        query: String,
        cached: CachedData<List<ElectoralUnit>>?,
        required: Boolean,
        refresh: MunicipalityRefresh,
        saving: SaveState,
        location: VoterLocation?,
    ): LocationUiState {
        val normalized = normalizeForSearch(query)
        val all = cached?.value.orEmpty()
        val filtered = if (normalized.isEmpty()) all else all.filter { normalizeForSearch(it.name).contains(normalized) }
        return LocationUiState(
            step = LocationStep.valueOf(stepName),
            fromSettings = route.fromSettings,
            currentLocation = location,
            selectedUf = uf,
            query = query,
            municipalities = filtered.sortedBy { normalizeForSearch(it.name) },
            municipalitiesLoad = loadStateOf(
                hasData = all.isNotEmpty(),
                error = refresh.error ?: cached?.lastError,
                isRefreshing = refresh.isRefreshing || cached == null,
            ),
            municipalityRequired = required,
            isSaving = saving.isSaving,
            done = saving.done,
        )
    }

    private data class SaveState(val isSaving: Boolean = false, val done: Boolean = false)

    companion object {
        private const val KEY_STEP = "step"
        private const val KEY_UF = "uf"
        private const val KEY_QUERY = "query"

        /** The DF has no municipal elections; voters abroad vote only for President. */
        fun asksMunicipality(uf: String): Boolean = uf != "DF" && uf != ElectoralUnit.ABROAD_CODE
    }
}
