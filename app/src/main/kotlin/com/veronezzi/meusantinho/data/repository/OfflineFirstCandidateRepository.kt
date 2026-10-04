package com.veronezzi.meusantinho.data.repository

import androidx.room.withTransaction
import com.veronezzi.meusantinho.core.common.DefaultDispatcher
import com.veronezzi.meusantinho.data.local.db.PublicCacheDatabase
import com.veronezzi.meusantinho.data.mapper.runningMateEntities
import com.veronezzi.meusantinho.data.mapper.toDomain
import com.veronezzi.meusantinho.data.mapper.toDomainOrNull
import com.veronezzi.meusantinho.data.mapper.toEntity
import com.veronezzi.meusantinho.data.remote.CandidateSourceSelector
import com.veronezzi.meusantinho.data.remote.DivulgaCandContasSource
import com.veronezzi.meusantinho.domain.model.AppResult
import com.veronezzi.meusantinho.domain.model.CachedData
import com.veronezzi.meusantinho.domain.model.Candidate
import com.veronezzi.meusantinho.domain.model.CandidateDetail
import com.veronezzi.meusantinho.domain.model.CandidateFilter
import com.veronezzi.meusantinho.domain.model.DataSource
import com.veronezzi.meusantinho.domain.model.Election
import com.veronezzi.meusantinho.domain.model.FilterOptions
import com.veronezzi.meusantinho.domain.model.filteredBy
import com.veronezzi.meusantinho.domain.model.normalizeForSearch
import com.veronezzi.meusantinho.domain.repository.CandidateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Candidate lists and details, offline-first (ARCHITECTURE.md 2.8). Lists come from
 * DivulgaCandContas or, when it refuses the request in a general election, from the TSE open
 * data ([CandidateSourceSelector]); `CachedData.source` tells which. Filtering runs in memory
 * on [defaultDispatcher] (at most ~1.500 rows per list).
 */
@Singleton
class OfflineFirstCandidateRepository @Inject constructor(
    private val db: PublicCacheDatabase,
    private val selector: CandidateSourceSelector,
    private val api: DivulgaCandContasSource,
    clock: Clock,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : CandidateRepository {
    private val policy = CachePolicy(clock)
    private val locks = KeyedMutex()
    private val electionDao = db.electionDao()
    private val candidateDao = db.candidateDao()
    private val detailDao = db.candidateDetailDao()
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
    }.flowOn(defaultDispatcher)

    override suspend fun refreshCandidates(
        election: Election,
        ueCode: String,
        officeCode: Int,
        force: Boolean,
    ): AppResult<Unit> {
        val key = FetchKeys.candidates(election.id, ueCode, officeCode)
        return locks.withLock(key) {
            val state = fetchStateDao.get(key)
            val ttl = policy.ttl(CacheKind.CANDIDATES, policy.isElectionWeek(listOf(election)))
            policy.resultWithoutFetch(state, ttl, force)?.let { return@withLock it }
            val sourced = selector.fetchCandidates(election, ueCode, officeCode, force)
            when (val result = sourced.result) {
                is AppResult.Success -> {
                    db.withTransaction {
                        candidateDao.delete(election.id, ueCode, officeCode)
                        candidateDao.insertAll(result.value.map { it.toEntity() })
                        fetchStateDao.upsert(policy.success(key, sourced.source))
                    }
                    AppResult.Success(Unit)
                }
                is AppResult.Failure -> {
                    fetchStateDao.upsert(policy.failure(key, state, result.error))
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
                        .sortedBy { normalizeForSearch(it.acronym) },
                    registrationStatuses = candidates.map { it.status.registration }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sortedBy { normalizeForSearch(it) },
                )
            }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)

    override fun observeCandidateDetail(electionId: Long, candidateId: Long): Flow<CachedData<CandidateDetail?>> =
        combine(
            detailDao.observe(electionId, candidateId),
            detailDao.observeRunningMates(electionId, candidateId),
            fetchStateDao.observe(FetchKeys.detail(electionId, candidateId)),
            electionDao.observe(electionId),
        ) { detail, runningMates, state, election ->
            val ttl = policy.ttl(CacheKind.DETAIL, policy.isElectionWeek(listOfNotNull(election?.toDomainOrNull())))
            policy.cachedData(detail?.toDomain(runningMates), state, ttl)
        }

    /** Details come only from DivulgaCandContas: the open data has no detail (no fallback here). */
    override suspend fun refreshCandidateDetail(
        election: Election,
        ueCode: String,
        candidateId: Long,
        force: Boolean,
    ): AppResult<Unit> {
        val key = FetchKeys.detail(election.id, candidateId)
        return locks.withLock(key) {
            val state = fetchStateDao.get(key)
            val ttl = policy.ttl(CacheKind.DETAIL, policy.isElectionWeek(listOf(election)))
            policy.resultWithoutFetch(state, ttl, force)?.let { return@withLock it }
            val officeHint = candidateDao.get(election.id, candidateId)?.columns?.officeCode
            when (val result = api.fetchCandidateDetail(election, ueCode, candidateId, officeHint)) {
                is AppResult.Success -> {
                    db.withTransaction {
                        detailDao.insert(result.value.toEntity())
                        detailDao.deleteRunningMates(election.id, candidateId)
                        detailDao.insertRunningMates(result.value.runningMateEntities())
                        fetchStateDao.upsert(policy.success(key, DataSource.DIVULGA_CAND_CONTAS))
                    }
                    AppResult.Success(Unit)
                }
                is AppResult.Failure -> {
                    fetchStateDao.upsert(policy.failure(key, state, result.error))
                    result
                }
            }
        }
    }
}
