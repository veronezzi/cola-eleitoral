package com.veronezzi.meusantinho.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veronezzi.meusantinho.domain.model.AppError
import com.veronezzi.meusantinho.domain.model.AppResult
import com.veronezzi.meusantinho.domain.model.BallotPick
import com.veronezzi.meusantinho.domain.model.Candidate
import com.veronezzi.meusantinho.domain.model.CandidateDetail
import com.veronezzi.meusantinho.domain.model.CandidateStatus
import com.veronezzi.meusantinho.domain.model.Election
import com.veronezzi.meusantinho.domain.model.ElectionScope
import com.veronezzi.meusantinho.domain.model.OfficeRules
import com.veronezzi.meusantinho.domain.model.Round
import com.veronezzi.meusantinho.domain.model.SavePickResult
import com.veronezzi.meusantinho.domain.model.normalizeForSearch
import com.veronezzi.meusantinho.domain.repository.BallotRepository
import com.veronezzi.meusantinho.domain.repository.CandidateRepository
import com.veronezzi.meusantinho.domain.repository.ElectionRepository
import com.veronezzi.meusantinho.domain.repository.SettingsRepository
import com.veronezzi.meusantinho.ui.common.AppClock
import com.veronezzi.meusantinho.ui.common.Freshness
import com.veronezzi.meusantinho.ui.common.TseLinks
import com.veronezzi.meusantinho.ui.common.candidateNumberText
import com.veronezzi.meusantinho.ui.common.isRoundOpen
import com.veronezzi.meusantinho.ui.common.toFreshness
import com.veronezzi.meusantinho.ui.navigation.CandidateDetailRoute
import com.veronezzi.meusantinho.ui.navigation.roundOf
import com.veronezzi.meusantinho.ui.screens.candidates.CandidateListViewModel
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The candidate against the vote the screen was opened for. */
sealed interface PickState {
    data object NotSaved : PickState

    data object SavedHere : PickState

    /** Already the pick of the other Senate vote: a repeated Senate vote is void on the urna. */
    data class InOtherSlot(val otherSlot: Int) : PickState

    /** The vote has another candidate; saving asks "Trocar X por Y?". */
    data class SlotTaken(val current: BallotPick) : PickState
}

/** One-off feedback, shown in a snackbar and then cleared with [CandidateDetailViewModel.onMessageShown]. */
sealed interface DetailMessage {
    /** [statusNotice] is the verbatim TSE status when the candidate is not (yet) fit to run. */
    data class Saved(val statusNotice: String?) : DetailMessage

    data class Removed(val pick: BallotPick) : DetailMessage

    data class Duplicate(val otherSlot: Int) : DetailMessage

    data class Failed(val error: AppError) : DetailMessage
}

sealed interface DetailContent {
    data object Loading : DetailContent

    data class Failed(val error: AppError) : DetailContent

    data object Loaded : DetailContent
}

data class CandidateDetailUiState(
    val content: DetailContent = DetailContent.Loading,
    val candidate: Candidate? = null,
    val detail: CandidateDetail? = null,
    val officeName: String = "",
    val unitName: String = "",
    val digitCount: Int = 0,
    val numberText: String = "",
    val slot: Int = 1,
    val maxPicks: Int = 1,
    val photoUrl: String? = null,
    val officialPageUrl: String = TseLinks.DIVULGA_HOME,
    /** The full detail could not be loaded; the screen shows what the list had. */
    val detailError: AppError? = null,
    val freshness: Freshness? = null,
    val pickState: PickState = PickState.NotSaved,
    /** False once the round's date has passed: picks can only be removed. */
    val canEdit: Boolean = true,
    val isSaving: Boolean = false,
    val confirmReplace: BallotPick? = null,
    val message: DetailMessage? = null,
) {
    val hasSeveralSeats: Boolean get() = maxPicks > 1
}

private data class Transient(
    val isRefreshing: Boolean = true,
    val refreshError: AppError? = null,
    val isSaving: Boolean = false,
    val confirmReplace: BallotPick? = null,
    val message: DetailMessage? = null,
)

/**
 * Candidate detail (ARCHITECTURE.md 4.5). Shows the cached list data at once and the full detail
 * when it arrives; the TSE texts are verbatim. "Salvar no meu santinho" saves a snapshot in the
 * route's vote: an occupied vote asks before replacing, and the same candidate is refused in the
 * other Senate vote. Nothing evaluative is said about any candidate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = CandidateDetailViewModel.Factory::class)
class CandidateDetailViewModel @AssistedInject constructor(
    @Assisted private val route: CandidateDetailRoute,
    private val candidateRepository: CandidateRepository,
    private val electionRepository: ElectionRepository,
    private val ballotRepository: BallotRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: AppClock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: CandidateDetailRoute): CandidateDetailViewModel
    }

    private val round: Round = roundOf(route.round)
    private val transient = MutableStateFlow(Transient())

    private val election = electionRepository.observeElections()
        .map { cached -> cached.value.firstOrNull { it.id == route.electionId } }
        .distinctUntilChanged()

    private val office = combine(election, settingsRepository.settings.map { it.location }) { e, l -> e to l }
        .distinctUntilChanged()
        .flatMapLatest { (election, location) ->
            if (election == null || location == null) {
                flowOf(null)
            } else {
                electionRepository.observeBallotOffices(election, location, round)
                    .map { offices -> offices.value.firstOrNull { it.code == route.officeCode } }
            }
        }

    private val sources = combine(
        candidateRepository.observeCandidateDetail(route.electionId, route.candidateId),
        candidateRepository.observeCandidates(route.electionId, route.ueCode, route.officeCode),
    ) { detail, list -> detail to list }

    val uiState: StateFlow<CandidateDetailUiState> = combine(
        sources,
        ballotRepository.observeBallot(route.electionId, round),
        combine(election, office) { e, o -> e to o },
        settingsRepository.settings.map { it.location },
        transient,
    ) { (detailData, listData), picks, (election, office), location, transient ->
        val detail = detailData.value
        val listCandidate = listData.value.firstOrNull { it.id == route.candidateId }
        val candidate = detail?.candidate ?: listCandidate
        val digitCount = office?.digitCount ?: OfficeRules.digitCount(route.officeCode) ?: candidate?.number?.toString()?.length ?: 0
        val detailError = transient.refreshError ?: detailData.lastError
        CandidateDetailUiState(
            content = when {
                candidate != null -> DetailContent.Loaded
                transient.isRefreshing -> DetailContent.Loading
                else -> DetailContent.Failed(detailError ?: AppError.NotFound)
            },
            candidate = candidate,
            detail = detail,
            officeName = office?.name ?: OfficeRules.canonicalName(route.officeCode).orEmpty(),
            unitName = CandidateListViewModel.unitName(route.ueCode, location),
            digitCount = digitCount,
            numberText = candidate?.let { candidateNumberText(it.number, digitCount) }.orEmpty(),
            slot = route.slot,
            maxPicks = office?.maxPicks ?: OfficeRules.maxPicks(route.officeCode, route.year, round),
            photoUrl = photoOf(detail, listCandidate),
            officialPageUrl = detail?.officialPageUrl?.let { TseLinks.officialOrHome(it) }
                ?: TseLinks.candidatePage(route.year, route.electionId, route.ueCode, route.candidateId),
            detailError = detailError.takeIf { detail == null },
            freshness = if (detail != null) detailData.toFreshness() else listData.toFreshness(),
            pickState = pickStateOf(picks),
            canEdit = election?.isRoundOpen(round, clock.todayInBrasilia()) ?: true,
            isSaving = transient.isSaving,
            confirmReplace = transient.confirmReplace,
            message = transient.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CandidateDetailUiState(slot = route.slot))

    init {
        refresh(force = false)
    }

    fun onRefresh() = refresh(force = true)

    /** "Salvar no meu santinho". */
    fun onSaveClick() {
        val state = uiState.value
        if (!state.canEdit || state.isSaving || state.candidate == null) return
        when (val pick = state.pickState) {
            PickState.SavedHere -> Unit
            is PickState.InOtherSlot -> transient.update { it.copy(message = DetailMessage.Duplicate(pick.otherSlot)) }
            is PickState.SlotTaken -> transient.update { it.copy(confirmReplace = pick.current) }
            PickState.NotSaved -> save()
        }
    }

    fun onConfirmReplace() {
        transient.update { it.copy(confirmReplace = null) }
        save()
    }

    fun onDismissReplace() {
        transient.update { it.copy(confirmReplace = null) }
    }

    /** "Remover do meu santinho", with undo in the snackbar. */
    fun onRemoveClick() {
        viewModelScope.launch {
            val pick = ballotRepository.observeBallot(route.electionId, round).first()
                .firstOrNull { it.officeCode == route.officeCode && it.slot == route.slot && it.candidateId == route.candidateId }
                ?: return@launch
            ballotRepository.removePick(pick.key)
            transient.update { it.copy(message = DetailMessage.Removed(pick)) }
        }
    }

    fun onUndoRemove(pick: BallotPick) {
        viewModelScope.launch { ballotRepository.savePick(pick) }
    }

    fun onMessageShown() {
        transient.update { it.copy(message = null) }
    }

    private fun save() {
        val state = uiState.value
        val candidate = state.candidate ?: return
        transient.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val office = office.first()
            val status = state.detail?.candidate?.status ?: candidate.status
            val pick = BallotPick(
                electionId = route.electionId,
                electionYear = route.year,
                round = round,
                officeCode = route.officeCode,
                officeName = state.officeName,
                urnaOrder = office?.urnaOrder ?: OfficeRules.urnaOrder(route.officeCode) ?: 0,
                digitCount = state.digitCount,
                slot = route.slot,
                ueCode = route.ueCode,
                candidateId = candidate.id,
                candidateNumber = state.numberText,
                ballotName = candidate.ballotName,
                partyAcronym = candidate.party.acronym,
                coalition = candidate.coalition,
                runningMateNames = state.detail?.runningMates?.map { it.ballotName }.orEmpty(),
                statusAtSave = status.registration,
                savedAt = clock.now(),
            )
            val message = when (val result = ballotRepository.savePick(pick)) {
                SavePickResult.Saved -> DetailMessage.Saved(statusNotice = status.registration.takeIf { status.needsNotice() })
                is SavePickResult.DuplicateCandidate -> DetailMessage.Duplicate(result.otherSlot)
                is SavePickResult.Failed -> DetailMessage.Failed(result.error)
            }
            transient.update { it.copy(isSaving = false, message = message) }
        }
    }

    private fun refresh(force: Boolean) {
        viewModelScope.launch {
            transient.update { it.copy(isRefreshing = true, refreshError = null) }
            val election = electionRepository.observeElections().first().value
                .firstOrNull { it.id == route.electionId }
                ?: Election(
                    id = route.electionId,
                    year = route.year,
                    name = "",
                    round = null,
                    scope = if (route.ueCode.all { it.isDigit() }) ElectionScope.MUNICIPAL else ElectionScope.GENERAL,
                    date = null,
                )
            val result = candidateRepository.refreshCandidateDetail(election, route.ueCode, route.candidateId, force)
            transient.update { it.copy(isRefreshing = false, refreshError = (result as? AppResult.Failure)?.error) }
        }
    }

    private fun pickStateOf(picks: List<BallotPick>): PickState {
        val sameOffice = picks.filter { it.officeCode == route.officeCode }
        val inThisSlot = sameOffice.firstOrNull { it.slot == route.slot }
        val elsewhere = sameOffice.firstOrNull { it.slot != route.slot && it.candidateId == route.candidateId }
        return when {
            inThisSlot?.candidateId == route.candidateId -> PickState.SavedHere
            elsewhere != null -> PickState.InOtherSlot(elsewhere.slot)
            inThisSlot != null -> PickState.SlotTaken(inThisSlot)
            else -> PickState.NotSaved
        }
    }

    /** Detail photo only when the TSE allows publishing it; list photos only for majoritarian offices. */
    private fun photoOf(detail: CandidateDetail?, listCandidate: Candidate?): String? = when {
        detail != null -> detail.candidate.photoUrl.takeIf { detail.photoPublishable != false }
        route.officeCode in setOf(OfficeRules.PRESIDENT, OfficeRules.GOVERNOR, OfficeRules.SENATOR, OfficeRules.MAYOR) ->
            listCandidate?.photoUrl
        else -> null
    }

    private fun CandidateStatus.needsNotice(): Boolean =
        isFit == false || !normalizeForSearch(registration).startsWith("deferido")
}
