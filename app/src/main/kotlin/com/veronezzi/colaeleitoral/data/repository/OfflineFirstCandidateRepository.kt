package com.veronezzi.colaeleitoral.data.repository

import com.veronezzi.colaeleitoral.core.common.DefaultDispatcher
import com.veronezzi.colaeleitoral.data.local.db.FetchStateEntity
import com.veronezzi.colaeleitoral.data.local.db.PublicCacheDatabase
import com.veronezzi.colaeleitoral.data.local.storageResult
import com.veronezzi.colaeleitoral.data.mapper.toDomain
import com.veronezzi.colaeleitoral.data.mapper.toDomainOrNull
import com.veronezzi.colaeleitoral.data.mapper.toEntity
import com.veronezzi.colaeleitoral.data.remote.CandidateSourceSelector
import com.veronezzi.colaeleitoral.data.remote.DivulgaCandContasSource
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.CachedData
import com.veronezzi.colaeleitoral.domain.model.Candidate
import com.veronezzi.colaeleitoral.domain.model.CandidateDetail
import com.veronezzi.colaeleitoral.domain.model.CandidateFilter
import com.veronezzi.colaeleitoral.domain.model.DataSource
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.FilterOptions
import com.veronezzi.colaeleitoral.domain.model.filteredBy
import com.veronezzi.colaeleitoral.domain.model.map
import com.veronezzi.colaeleitoral.domain.repository.CandidateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Candidate lists and details, offline-first (ARCHITECTURE.md 2.8). Lists come from
 * DivulgaCandContas or, when it refuses the request in a general election, from the TSE open
 * data ([CandidateSourceSelector]); `CachedData.source` tells which. Filtering runs in memory
 * on [defaultDispatcher] (at most ~1.500 rows per list). Details are kept only in memory
 * ([CandidateDetailMemoryCache], S2). Storage failures (disk full) come back as
 * `AppError.Storage`, never as exceptions.
 */
@Singleton
class OfflineFirstCandidateRepository @Inject constructor(
    private val db: PublicCacheDatabase,
    private val selector: CandidateSourceSelector,
    private val api: DivulgaCandContasSource,
    private val policy: CachePolicy,
    private val details: CandidateDetailMemoryCache,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : CandidateRepository {
    private val locks = KeyedMutex()
    private val electionDao = db.electionDao()
    private val candidateDao = db.candidateDao()
    private val fetchStateDao = db.fetchStateDao()

    override fun observeCandidates(
        electionId: Long,
        ueCode: String,
        officeCode: Int,
        filter: CandidateFilter,
    ): Flow<CachedData<List<Candidate>>> = combine(
        candidateDao.observe(electionId, ueCode, officeCode),
        fetchStateDao.observe(FetchKeys.candidates(electionId, ueCode, officeCode)),
        electionDao.observe(electionId),
    ) { rows, state, election ->
        val ttl = policy.ttl(CacheKind.CANDIDATES, policy.isElectionWeek(listOfNotNull(election?.toDomainOrNull())))
        policy.cachedData(rows.map { it.toDomain() }.filteredBy(filter), state, ttl)
    }.distinctUntilChanged().flowOn(defaultDispatcher)

    override suspend fun refreshCandidates(
        election: Election,
        ueCode: String,
        officeCode: Int,
        force: Boolean,
    ): AppResult<Unit> {
        val key = FetchKeys.candidates(election.id, ueCode, officeCode)
        return locks.withLock(key) {
            val state = when (val read = storageResult { fetchStateDao.get(key) }) {
                is AppResult.Success -> read.value
                is AppResult.Failure -> return@withLock read
            }
            val ttl = policy.ttl(CacheKind.CANDIDATES, policy.isElectionWeek(listOf(election)))
            policy.resultWithoutFetch(state, ttl, force)?.let { return@withLock it }
            val generation = policy.cacheGeneration()
            val sourced = selector.fetchCandidates(election, ueCode, officeCode, force)
            when (val result = sourced.result) {
                is AppResult.Success -> db.writeCache(policy, generation) {
                    candidateDao.delete(election.id, ueCode, officeCode)
                    candidateDao.insertAll(result.value.map { it.toEntity() })
                    fetchStateDao.upsert(policy.success(key, sourced.source))
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

    /** Parties (by acronym, alphabetical) and verbatim status texts present in the cached list. */
    override fun observeFilterOptions(electionId: Long, ueCode: String, officeCode: Int): Flow<FilterOptions> =
        candidateDao.observe(electionId, ueCode, officeCode)
            .map { rows ->
                val candidates = rows.map { it.toDomain() }
                FilterOptions(
                    parties = candidates.map { it.party }
                        .groupBy { it.acronym }
                        .map { (_, parties) -> parties.firstOrNull { it.name != null } ?: parties.first() }
                        .sortedByNormalized { it.acronym },
                    registrationStatuses = candidates.map { it.status.registration }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sortedByNormalized { it },
                )
            }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)

    override fun observeCandidateDetail(electionId: Long, candidateId: Long): Flow<CachedData<CandidateDetail?>> =
        combine(details.observe(FetchKeys.detail(electionId, candidateId)), electionDao.observe(electionId)) { entry, election ->
            val ttl = policy.ttl(CacheKind.DETAIL, policy.isElectionWeek(listOfNotNull(election?.toDomainOrNull())))
            policy.cachedData(entry?.detail, entry?.state, ttl)
        }.distinctUntilChanged().flowOn(defaultDispatcher)

    /**
     * Details come only from DivulgaCandContas (the open data has no detail) and stay in memory:
     * no row, no `fetch_state` key, nothing on disk (S2).
     */
    override suspend fun refreshCandidateDetail(
        election: Election,
        ueCode: String,
        candidateId: Long,
        force: Boolean,
    ): AppResult<Unit> {
        val key = FetchKeys.detail(election.id, candidateId)
        return locks.withLock(key) {
            val previous = details.get(key)
            val ttl = policy.ttl(CacheKind.DETAIL, policy.isElectionWeek(listOf(election)))
            policy.resultWithoutFetch(previous?.state, ttl, force)?.let { return@withLock it }
            val generation = policy.cacheGeneration()
            val officeHint = storageResult { candidateDao.get(election.id, candidateId) }.valueOrNull()?.columns?.officeCode
            val result = api.fetchCandidateDetail(election, ueCode, candidateId, officeHint)
            if (policy.isCurrent(generation)) {
                val entry = when (result) {
                    is AppResult.Success ->
                        CandidateDetailMemoryCache.Entry(result.value, policy.success(key, DataSource.DIVULGA_CAND_CONTAS))
                    is AppResult.Failure ->
                        CandidateDetailMemoryCache.Entry(previous?.detail, policy.failure(key, previous?.state, result.error))
                }
                details.put(key, entry)
            }
            result.map { }
        }
    }

    /** Best effort: on a full disk even this small write may fail, and the result stands anyway. */
    private suspend fun recordFailure(key: String, previous: FetchStateEntity?, error: AppError, generation: Long) {
        db.writeCache(policy, generation) { fetchStateDao.upsert(policy.failure(key, previous, error)) }
    }
}
