package com.veronezzi.colaeleitoral.data.repository

import com.veronezzi.colaeleitoral.core.common.DefaultDispatcher
import com.veronezzi.colaeleitoral.core.common.IoDispatcher
import com.veronezzi.colaeleitoral.data.local.db.FetchStateEntity
import com.veronezzi.colaeleitoral.data.local.db.OfficeEntity
import com.veronezzi.colaeleitoral.data.local.db.PublicCacheDatabase
import com.veronezzi.colaeleitoral.data.local.storageResult
import com.veronezzi.colaeleitoral.data.mapper.AppErrorCodec
import com.veronezzi.colaeleitoral.data.mapper.toDomain
import com.veronezzi.colaeleitoral.data.mapper.toDomainOrNull
import com.veronezzi.colaeleitoral.data.mapper.toEntity
import com.veronezzi.colaeleitoral.data.mapper.toMunicipalityEntity
import com.veronezzi.colaeleitoral.data.remote.CandidateSourceSelector
import com.veronezzi.colaeleitoral.data.remote.DivulgaCandContasSource
import com.veronezzi.colaeleitoral.data.remote.opendata.OpenDataFileStore
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.CachedData
import com.veronezzi.colaeleitoral.domain.model.DataSource
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectionScope
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import com.veronezzi.colaeleitoral.domain.model.Office
import com.veronezzi.colaeleitoral.domain.model.OfficeRules
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.domain.repository.ElectionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Elections, municipalities and ballot offices, offline-first (ARCHITECTURE.md 2.8): reads emit
 * the Room cache at once (mapped and sorted on [defaultDispatcher]); `refresh*` downloads only
 * when the TTL expired (or [force]) and replaces the key's rows in one transaction. A failed
 * refresh never deletes cached rows, and a storage failure (disk full) is `AppError.Storage`.
 */
@Singleton
class OfflineFirstElectionRepository @Inject constructor(
    private val db: PublicCacheDatabase,
    private val api: DivulgaCandContasSource,
    private val selector: CandidateSourceSelector,
    private val openDataFiles: OpenDataFileStore,
    private val details: CandidateDetailMemoryCache,
    private val policy: CachePolicy,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ElectionRepository {
    private val locks = KeyedMutex()
    private val electionDao = db.electionDao()
    private val municipalityDao = db.municipalityDao()
    private val officeDao = db.officeDao()
    private val fetchStateDao = db.fetchStateDao()

    override fun observeElections(): Flow<CachedData<List<Election>>> =
        combine(electionDao.observeAll(), fetchStateDao.observe(FetchKeys.ELECTIONS)) { rows, state ->
            val elections = rows.mapNotNull { it.toDomainOrNull() }.sortedWith(NEWEST_FIRST)
            policy.cachedData(elections, state, policy.ttl(CacheKind.ELECTIONS, policy.isElectionWeek(elections)))
        }.distinctUntilChanged().flowOn(defaultDispatcher)

    override suspend fun refreshElections(force: Boolean): AppResult<Unit> = locks.withLock(FetchKeys.ELECTIONS) {
        val key = FetchKeys.ELECTIONS
        val (state, cached) = when (
            val read = storageResult { fetchStateDao.get(key) to electionDao.getAll().mapNotNull { it.toDomainOrNull() } }
        ) {
            is AppResult.Success -> read.value
            is AppResult.Failure -> return@withLock read
        }
        val ttl = policy.ttl(CacheKind.ELECTIONS, policy.isElectionWeek(cached))
        policy.resultWithoutFetch(state, ttl, force)?.let { return@withLock it }
        val generation = policy.cacheGeneration()
        when (val result = api.fetchElections()) {
            is AppResult.Success -> db.writeCache(policy, generation) {
                electionDao.deleteAll()
                electionDao.insertAll(result.value.map { it.toEntity() })
                fetchStateDao.upsert(policy.success(key, DataSource.DIVULGA_CAND_CONTAS))
            }.also { written ->
                if (written is AppResult.Failure) recordFailure(key, state, written.error, generation)
            }
            is AppResult.Failure -> {
                val seed = if (cached.isEmpty()) ElectionSeed.electionsFor(policy.today()) else emptyList()
                db.writeCache(policy, generation) {
                    if (seed.isNotEmpty()) {
                        electionDao.insertAll(seed.map { it.toEntity() })
                        fetchStateDao.upsert(seedState(key, result.error))
                    } else {
                        fetchStateDao.upsert(policy.failure(key, state, result.error))
                    }
                }
                result
            }
        }
    }

    override fun observeMunicipalities(uf: String): Flow<CachedData<List<ElectoralUnit>>> {
        if (uf == ElectoralUnit.ABROAD_CODE) return flowOf(CachedData(emptyList(), fetchedAt = null, isStale = false))
        // Sorted by the DAO on the normalized name stored with each row (computed once on insert).
        return combine(municipalityDao.observeByUf(uf), fetchStateDao.observe(FetchKeys.municipalities(uf))) { rows, state ->
            policy.cachedData(rows.map { it.toDomain() }, state, CacheKind.MUNICIPALITIES.normalTtl)
        }.distinctUntilChanged().flowOn(defaultDispatcher)
    }

    override suspend fun refreshMunicipalities(uf: String, force: Boolean): AppResult<Unit> {
        if (uf == ElectoralUnit.ABROAD_CODE) return AppResult.Success(Unit)
        val key = FetchKeys.municipalities(uf)
        return locks.withLock(key) {
            val state = when (val read = storageResult { fetchStateDao.get(key) }) {
                is AppResult.Success -> read.value
                is AppResult.Failure -> return@withLock read
            }
            policy.resultWithoutFetch(state, CacheKind.MUNICIPALITIES.normalTtl, force)?.let { return@withLock it }
            // E2 needs the id of the latest municipal election, so the election list comes first.
            val municipalElection = latestMunicipalElection() ?: run {
                val electionsResult = refreshElections()
                latestMunicipalElection()
                    ?: return@withLock (electionsResult as? AppResult.Failure) ?: AppResult.Failure(AppError.NotFound)
            }
            val generation = policy.cacheGeneration()
            when (val result = api.fetchMunicipalities(uf, municipalElection.id)) {
                is AppResult.Success -> db.writeCache(policy, generation) {
                    municipalityDao.deleteByUf(uf)
                    municipalityDao.insertAll(result.value.map { it.toMunicipalityEntity(municipalElection.id) })
                    fetchStateDao.upsert(policy.success(key, DataSource.DIVULGA_CAND_CONTAS))
                }.also { written ->
                    if (written is AppResult.Failure) recordFailure(key, state, written.error, generation)
                }
                is AppResult.Failure -> {
                    recordFailure(key, state, result.error, generation)
                    result
                }
            }
        }
    }

    override fun observeBallotOffices(
        election: Election,
        location: VoterLocation,
        round: Round,
    ): Flow<CachedData<List<Office>>> {
        val units = location.ballotUnitCodes(election.scope)
        if (units.isEmpty()) return flowOf(CachedData(emptyList(), fetchedAt = null, isStale = false))
        val perUnit = units.map { ueCode ->
            combine(
                officeDao.observe(election.id, ueCode),
                fetchStateDao.observe(FetchKeys.offices(election.id, ueCode)),
            ) { rows, state -> UnitOffices(ueCode, rows, state) }
        }
        return combine(perUnit) { unitOffices -> ballotOf(election, location, round, unitOffices.toList()) }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)
    }

    override suspend fun refreshBallotOffices(
        election: Election,
        location: VoterLocation,
        force: Boolean,
    ): AppResult<Unit> {
        var firstFailure: AppResult.Failure? = null
        for (ueCode in location.ballotUnitCodes(election.scope)) {
            val result = refreshOffices(election, ueCode, force)
            if (result is AppResult.Failure && firstFailure == null) firstFailure = result
        }
        return firstFailure ?: AppResult.Success(Unit)
    }

    /**
     * Refreshes still in flight drop their results ([CachePolicy.onCacheCleared]) and open-data
     * downloads are cancelled, so this never waits for the network. Best effort on a broken
     * disk: whatever could not be deleted now is retried by the next clear.
     */
    override suspend fun clearCache() {
        policy.onCacheCleared()
        openDataFiles.clear()
        details.clear()
        selector.reset()
        storageResult { withContext(ioDispatcher) { db.clearAllTables() } }
    }

    private suspend fun refreshOffices(election: Election, ueCode: String, force: Boolean): AppResult<Unit> {
        val key = FetchKeys.offices(election.id, ueCode)
        return locks.withLock(key) {
            val state = when (val read = storageResult { fetchStateDao.get(key) }) {
                is AppResult.Success -> read.value
                is AppResult.Failure -> return@withLock read
            }
            val ttl = policy.ttl(CacheKind.OFFICES, policy.isElectionWeek(listOf(election)))
            policy.resultWithoutFetch(state, ttl, force)?.let { return@withLock it }
            val generation = policy.cacheGeneration()
            when (val result = api.fetchOffices(election, ueCode)) {
                is AppResult.Success -> db.writeCache(policy, generation) {
                    officeDao.delete(election.id, ueCode)
                    officeDao.insertAll(result.value.map { it.toEntity(election.id, ueCode) })
                    fetchStateDao.upsert(policy.success(key, DataSource.DIVULGA_CAND_CONTAS))
                }.also { written ->
                    if (written is AppResult.Failure) recordFailure(key, state, written.error, generation)
                }
                is AppResult.Failure -> {
                    recordFailure(key, state, result.error, generation)
                    result
                }
            }
        }
    }

    /** Best effort: on a full disk even this small write may fail, and the result stands anyway. */
    private suspend fun recordFailure(key: String, previous: FetchStateEntity?, error: AppError, generation: Long) {
        db.writeCache(policy, generation) { fetchStateDao.upsert(policy.failure(key, previous, error)) }
    }

    /**
     * The ballot in urna order. Each unit uses the offices the TSE listed for it; a unit with no
     * cached list uses the offices defined by law ([OfficeRules.defaultCodes]), so the ballot
     * always renders. `fetchedAt` is the oldest download among the units (null if one never
     * downloaded).
     */
    private fun ballotOf(
        election: Election,
        location: VoterLocation,
        round: Round,
        units: List<UnitOffices>,
    ): CachedData<List<Office>> {
        val ttl = policy.ttl(CacheKind.OFFICES, policy.isElectionWeek(listOf(election)))
        val offices = units.flatMap { unit ->
            val listed = unit.rows.filter { OfficeRules.ueCodeFor(it.code, location, election.scope) == unit.ueCode }
            val entries = if (listed.isNotEmpty()) {
                listed.map { OfficeEntry(it.code, it.name, it.candidateCount) }
            } else {
                OfficeRules.defaultCodes(election.scope, location.uf)
                    .filter { OfficeRules.ueCodeFor(it, location, election.scope) == unit.ueCode }
                    .mapNotNull { code -> OfficeRules.canonicalName(code)?.let { OfficeEntry(code, it, null) } }
            }
            entries.mapNotNull { OfficeRules.office(it.code, it.name, unit.ueCode, election.year, round, it.candidateCount) }
        }.distinctBy { it.code }.sortedBy { it.urnaOrder }
        val states = units.map { it.state }
        val fetchedAt = if (states.any { it?.fetchedAt == null }) null else states.mapNotNull { it?.fetchedAt }.minOrNull()
        return CachedData(
            value = offices,
            fetchedAt = fetchedAt?.let(Instant::ofEpochMilli),
            isStale = states.any { policy.isStale(it, ttl) },
            lastError = states.firstNotNullOfOrNull { it?.lastError }?.let(AppErrorCodec::decode),
            source = DataSource.DIVULGA_CAND_CONTAS,
        )
    }

    private suspend fun latestMunicipalElection(): Election? = storageResult { electionDao.getAll() }
        .valueOrNull().orEmpty()
        .mapNotNull { it.toDomainOrNull() }
        .filter { it.scope == ElectionScope.MUNICIPAL }
        .maxWithOrNull(compareBy<Election> { it.year }.thenBy { it.date ?: LocalDate.MIN })

    /** The snapshot counts as data captured at [ElectionSeed.CAPTURED_AT], already stale. */
    private fun seedState(key: String, error: AppError) = FetchStateEntity(
        fetchKey = key,
        fetchedAt = ElectionSeed.CAPTURED_AT.toEpochMilli(),
        lastAttemptAt = policy.now().toEpochMilli(),
        lastError = AppErrorCodec.encode(error),
        source = DataSource.DIVULGA_CAND_CONTAS.name,
    )

    private class UnitOffices(val ueCode: String, val rows: List<OfficeEntity>, val state: FetchStateEntity?)

    private class OfficeEntry(val code: Int, val name: String, val candidateCount: Int?)

    private companion object {
        val NEWEST_FIRST: Comparator<Election> = compareByDescending<Election> { it.year }
            .thenByDescending { it.date ?: LocalDate.MIN }
            .thenByDescending { it.id }
    }
}
