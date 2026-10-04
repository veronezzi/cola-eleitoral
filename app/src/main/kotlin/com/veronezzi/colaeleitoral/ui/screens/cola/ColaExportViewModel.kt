package com.veronezzi.colaeleitoral.ui.screens.cola

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.domain.model.Office
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.repository.BallotRepository
import com.veronezzi.colaeleitoral.domain.repository.ElectionRepository
import com.veronezzi.colaeleitoral.domain.repository.SettingsRepository
import com.veronezzi.colaeleitoral.ui.common.BallotSlot
import com.veronezzi.colaeleitoral.ui.common.buildBallotSlots
import com.veronezzi.colaeleitoral.ui.navigation.ColaExportRoute
import com.veronezzi.colaeleitoral.ui.navigation.roundOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class ColaUiState(
    val isLoading: Boolean = true,
    val electionName: String = "",
    val round: Round = Round.FIRST,
    val date: LocalDate? = null,
    /** Every vote of the ballot, empty boxes included (the voter can fill them by hand). */
    val slots: List<BallotSlot> = emptyList(),
    /** "Mostrar nomes" (on by default): names and parties next to the numbers. */
    val showNames: Boolean = true,
)

/**
 * Data of the printable cola (ARCHITECTURE.md 4.7): the ballot of one election round in urna
 * order with the saved picks. Works offline: without offices it falls back to the snapshots.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ColaExportViewModel.Factory::class)
class ColaExportViewModel @AssistedInject constructor(
    @Assisted private val route: ColaExportRoute,
    private val savedStateHandle: SavedStateHandle,
    electionRepository: ElectionRepository,
    ballotRepository: BallotRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: ColaExportRoute): ColaExportViewModel
    }

    private val round = roundOf(route.round)
    private val showNames = savedStateHandle.getStateFlow(KEY_SHOW_NAMES, true)

    private val election = electionRepository.observeElections()
        .map { cached -> cached.value.firstOrNull { it.id == route.electionId } }
        .distinctUntilChanged()

    private val offices = combine(election, settingsRepository.settings.map { it.location }) { e, l -> e to l }
        .distinctUntilChanged()
        .flatMapLatest { (election, location) ->
            if (election == null || location == null) {
                flowOf(emptyList())
            } else {
                electionRepository.observeBallotOffices(election, location, round).map { it.value }
            }
        }

    val uiState: StateFlow<ColaUiState> = combine(
        election,
        offices,
        ballotRepository.observeBallot(route.electionId, round),
        showNames,
    ) { election, offices, picks, showNames ->
        ColaUiState(
            isLoading = false,
            electionName = election?.name.orEmpty(),
            round = round,
            date = election?.dateOf(round),
            slots = if (offices.isNotEmpty()) buildBallotSlots(offices, picks) else snapshotSlots(picks),
            showNames = showNames,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ColaUiState(round = round))

    fun onShowNamesChange(show: Boolean) {
        savedStateHandle[KEY_SHOW_NAMES] = show
    }

    private fun snapshotSlots(picks: List<BallotPick>): List<BallotSlot> {
        val offices = picks
            .groupBy { it.officeCode }
            .map { (code, officePicks) ->
                val first = officePicks.first()
                Office(code, first.officeName, first.digitCount, first.urnaOrder, officePicks.maxOf { it.slot }, first.ueCode)
            }
        return buildBallotSlots(offices, picks)
    }

    private companion object {
        const val KEY_SHOW_NAMES = "showNames"
    }
}
