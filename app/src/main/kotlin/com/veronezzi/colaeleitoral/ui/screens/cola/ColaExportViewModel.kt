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
import com.veronezzi.colaeleitoral.ui.common.tryLocalWrite
import com.veronezzi.colaeleitoral.ui.navigation.ColaExportRoute
import com.veronezzi.colaeleitoral.ui.navigation.roundOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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
    /** The PNG to share is being written. */
    val isExporting: Boolean = false,
    /** Written image waiting for the share sheet; the screen opens it and calls `onShareHandled`. */
    val shareFile: File? = null,
    val message: ColaMessage? = null,
    /** Saved picks can't be read right now (nothing was deleted): banner with retry. */
    val picksUnavailable: Boolean = false,
)

sealed interface ColaMessage {
    /** The image could not be written (disk full, I/O error). */
    data object ImageFailed : ColaMessage

    /** No app on the device can receive the image. */
    data object ShareUnavailable : ColaMessage
}

private data class ExportState(
    val isExporting: Boolean = false,
    val shareFile: File? = null,
    val message: ColaMessage? = null,
)

/**
 * Data of the printable cola (ARCHITECTURE.md 4.7): the ballot of one election round in urna
 * order with the saved picks. Works offline: without offices it falls back to the snapshots. The
 * image to share is written here, off the main thread, so a full disk shows a message instead of
 * closing the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ColaExportViewModel.Factory::class)
class ColaExportViewModel @AssistedInject constructor(
    @Assisted private val route: ColaExportRoute,
    private val savedStateHandle: SavedStateHandle,
    electionRepository: ElectionRepository,
    private val ballotRepository: BallotRepository,
    settingsRepository: SettingsRepository,
    private val imageWriter: ColaImageWriter,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: ColaExportRoute): ColaExportViewModel
    }

    private val round = roundOf(route.round)
    private val showNames = savedStateHandle.getStateFlow(KEY_SHOW_NAMES, true)
    private val export = MutableStateFlow(ExportState())

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
        combine(export, ballotRepository.observeUnavailable()) { export, unavailable -> export to unavailable },
    ) { election, offices, picks, showNames, (export, unavailable) ->
        ColaUiState(
            isLoading = false,
            electionName = election?.name.orEmpty(),
            round = round,
            date = election?.dateOf(round),
            slots = if (offices.isNotEmpty()) buildBallotSlots(offices, picks) else snapshotSlots(picks),
            showNames = showNames,
            isExporting = export.isExporting,
            shareFile = export.shareFile,
            message = export.message,
            picksUnavailable = unavailable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ColaUiState(round = round))

    fun onShowNamesChange(show: Boolean) {
        savedStateHandle[KEY_SHOW_NAMES] = show
    }

    /** The user confirmed the share warning: writes the PNG of [content] (localized by the screen). */
    fun onShareConfirmed(content: ColaContent) {
        if (export.value.isExporting) return
        export.update { it.copy(isExporting = true, message = null) }
        viewModelScope.launch {
            var file: File? = null
            try {
                file = tryLocalWrite { imageWriter.write(content) }
            } finally {
                export.update { it.copy(isExporting = false, shareFile = file, message = if (file == null) ColaMessage.ImageFailed else null) }
            }
        }
    }

    /** The share sheet was opened ([delivered]) or no app could receive the image. */
    fun onShareHandled(delivered: Boolean) {
        export.update { it.copy(shareFile = null, message = if (delivered) it.message else ColaMessage.ShareUnavailable) }
    }

    fun onMessageShown() {
        export.update { it.copy(message = null) }
    }

    /** "Tentar de novo" on the banner shown while the picks can't be read. */
    fun onRetryRead() {
        viewModelScope.launch { tryLocalWrite { ballotRepository.retryRead() } }
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
