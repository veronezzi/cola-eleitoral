package com.veronezzi.colaeleitoral.ui.testing

import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.domain.model.BallotSlotKey
import com.veronezzi.colaeleitoral.domain.model.CachedData
import com.veronezzi.colaeleitoral.domain.model.Candidate
import com.veronezzi.colaeleitoral.domain.model.CandidateDetail
import com.veronezzi.colaeleitoral.domain.model.CandidateFilter
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import com.veronezzi.colaeleitoral.domain.model.FilterOptions
import com.veronezzi.colaeleitoral.domain.model.Office
import com.veronezzi.colaeleitoral.domain.model.OfficeRules
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.SavePickResult
import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.domain.model.filteredBy
import com.veronezzi.colaeleitoral.domain.repository.BallotRepository
import com.veronezzi.colaeleitoral.domain.repository.CandidateRepository
import com.veronezzi.colaeleitoral.domain.repository.ElectionRepository
import com.veronezzi.colaeleitoral.domain.repository.ReminderScheduler
import com.veronezzi.colaeleitoral.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate

/** In-memory repositories for ViewModel and Compose tests. Behavior follows the domain KDoc. */

class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = state

    /** When set, every write fails with this error and leaves the settings unchanged (disk full). */
    var writeError: AppError? = null

    private fun write(change: (UserSettings) -> UserSettings): AppResult<Unit> {
        writeError?.let { return AppResult.Failure(it) }
        state.update(change)
        return AppResult.Success(Unit)
    }

    override suspend fun setLocation(location: VoterLocation) = write { it.copy(location = location) }

    override suspend fun completeOnboarding(version: Int) =
        write { it.copy(onboardingCompleted = true, acceptedDisclaimerVersion = version) }

    override suspend fun setReminderEnabled(enabled: Boolean) = write { it.copy(reminderEnabled = enabled) }

    override suspend fun setAppLockEnabled(enabled: Boolean) = write { it.copy(appLockEnabled = enabled) }

    override suspend fun setSecureScreens(enabled: Boolean) = write { it.copy(secureScreens = enabled) }

    override suspend fun clear() = write { UserSettings() }
}

class FakeElectionRepository(
    elections: List<Election> = emptyList(),
    fetchedAt: Instant? = if (elections.isEmpty()) null else Instant.EPOCH,
) : ElectionRepository {
    val elections = MutableStateFlow(CachedData(elections, fetchedAt, isStale = false))

    /** Offices of the first round; the second round keeps only offices with a runoff. */
    val offices = MutableStateFlow(CachedData(emptyList<Office>(), null, isStale = false))
    val municipalities = mutableMapOf<String, MutableStateFlow<CachedData<List<ElectoralUnit>>>>()
    var refreshElectionsResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshMunicipalitiesResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshElectionsCalls = 0
    var cacheCleared = false

    override fun observeElections(): Flow<CachedData<List<Election>>> = elections

    override suspend fun refreshElections(force: Boolean): AppResult<Unit> {
        refreshElectionsCalls += 1
        return refreshElectionsResult
    }

    override fun observeMunicipalities(uf: String): Flow<CachedData<List<ElectoralUnit>>> = municipalityFlow(uf)

    override suspend fun refreshMunicipalities(uf: String, force: Boolean): AppResult<Unit> = refreshMunicipalitiesResult

    override fun observeBallotOffices(
        election: Election,
        location: VoterLocation,
        round: Round,
    ): Flow<CachedData<List<Office>>> = offices.map { cached ->
        cached.copy(
            value = cached.value.mapNotNull { office ->
                OfficeRules.office(office.code, office.name, office.ueCode, election.year, round, office.candidateCount)
            },
        )
    }

    override suspend fun refreshBallotOffices(election: Election, location: VoterLocation, force: Boolean) =
        AppResult.Success(Unit)

    override suspend fun clearCache() {
        cacheCleared = true
    }

    fun municipalityFlow(uf: String) = municipalities.getOrPut(uf) {
        MutableStateFlow(CachedData(emptyList(), null, isStale = false))
    }
}

class FakeCandidateRepository : CandidateRepository {
    private val lists = mutableMapOf<Triple<Long, String, Int>, MutableStateFlow<CachedData<List<Candidate>>>>()
    private val details = mutableMapOf<Pair<Long, Long>, MutableStateFlow<CachedData<CandidateDetail?>>>()
    var refreshCandidatesResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshDetailResult: AppResult<Unit> = AppResult.Success(Unit)
    val refreshedLists = mutableListOf<Triple<Long, String, Int>>()

    fun listFlow(electionId: Long, ueCode: String, officeCode: Int) = lists.getOrPut(Triple(electionId, ueCode, officeCode)) {
        MutableStateFlow(CachedData(emptyList(), null, isStale = false))
    }

    fun detailFlow(electionId: Long, candidateId: Long) = details.getOrPut(electionId to candidateId) {
        MutableStateFlow(CachedData(null, null, isStale = false))
    }

    fun setCandidates(electionId: Long, ueCode: String, officeCode: Int, candidates: List<Candidate>) {
        listFlow(electionId, ueCode, officeCode).value = CachedData(candidates, Instant.EPOCH, isStale = false)
    }

    override fun observeCandidates(
        electionId: Long,
        ueCode: String,
        officeCode: Int,
        filter: CandidateFilter,
    ): Flow<CachedData<List<Candidate>>> = listFlow(electionId, ueCode, officeCode).map { it.copy(value = it.value.filteredBy(filter)) }

    override suspend fun refreshCandidates(election: Election, ueCode: String, officeCode: Int, force: Boolean): AppResult<Unit> {
        refreshedLists += Triple(election.id, ueCode, officeCode)
        return refreshCandidatesResult
    }

    override fun observeFilterOptions(electionId: Long, ueCode: String, officeCode: Int): Flow<FilterOptions> =
        listFlow(electionId, ueCode, officeCode).map { cached ->
            FilterOptions(
                parties = cached.value.map { it.party }.distinctBy { it.acronym }.sortedBy { it.acronym },
                registrationStatuses = cached.value.map { it.status.registration }.distinct().sorted(),
            )
        }

    override fun observeCandidateDetail(electionId: Long, candidateId: Long): Flow<CachedData<CandidateDetail?>> =
        detailFlow(electionId, candidateId)

    override suspend fun refreshCandidateDetail(
        election: Election,
        ueCode: String,
        candidateId: Long,
        force: Boolean,
    ): AppResult<Unit> = refreshDetailResult
}

class FakeBallotRepository(initial: List<BallotPick> = emptyList()) : BallotRepository {
    val picks = MutableStateFlow(initial)
    val picksLost = MutableStateFlow(false)
    val unavailable = MutableStateFlow(false)
    var deletedAll = false
    var retryReadCalls = 0

    /** When set, every write throws it, as a full disk would (IOException). */
    var writeFailure: Throwable? = null

    override fun observePicksLost(): Flow<Boolean> = picksLost

    override suspend fun acknowledgePicksLost() {
        picksLost.value = false
    }

    override fun observeUnavailable(): Flow<Boolean> = unavailable

    override suspend fun retryRead() {
        retryReadCalls += 1
    }

    override fun observeBallot(electionId: Long, round: Round): Flow<List<BallotPick>> = picks.map { all ->
        all.filter { it.electionId == electionId && it.round == round }.sortedWith(compareBy({ it.urnaOrder }, { it.slot }))
    }

    override suspend fun savePick(pick: BallotPick): SavePickResult {
        writeFailure?.let { throw it }
        if (unavailable.value) return SavePickResult.Failed(AppError.Storage)
        val other = picks.value.firstOrNull {
            it.electionId == pick.electionId && it.round == pick.round && it.officeCode == pick.officeCode &&
                it.slot != pick.slot && it.candidateId == pick.candidateId
        }
        if (other != null) return SavePickResult.DuplicateCandidate(other.slot)
        picks.update { list -> list.filterNot { it.key == pick.key } + pick }
        return SavePickResult.Saved
    }

    override suspend fun removePick(key: BallotSlotKey) {
        writeFailure?.let { throw it }
        picks.update { list -> list.filterNot { it.key == key } }
    }

    override suspend fun clearBallot(electionId: Long, round: Round) {
        writeFailure?.let { throw it }
        picks.update { list -> list.filterNot { it.electionId == electionId && it.round == round } }
    }

    override suspend fun deleteAll() {
        writeFailure?.let { throw it }
        picks.value = emptyList()
        deletedAll = true
    }
}

class FakeReminderScheduler : ReminderScheduler {
    val scheduled = mutableListOf<Triple<Long, Round, LocalDate>>()
    var cancelledAll = false

    override suspend fun schedule(election: Election, round: Round, date: LocalDate) {
        scheduled += Triple(election.id, round, date)
    }

    override suspend fun cancel(electionId: Long, round: Round) {
        scheduled.removeAll { it.first == electionId && it.second == round }
    }

    override suspend fun cancelAll() {
        scheduled.clear()
        cancelledAll = true
    }
}
