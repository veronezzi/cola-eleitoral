package com.veronezzi.colaeleitoral.data.repository

import androidx.room.withTransaction
import com.veronezzi.colaeleitoral.data.local.db.FetchStateEntity
import com.veronezzi.colaeleitoral.data.local.db.PublicCacheDatabase
import com.veronezzi.colaeleitoral.data.local.storageResult
import com.veronezzi.colaeleitoral.data.mapper.toDomainOrNull
import com.veronezzi.colaeleitoral.data.remote.opendata.OpenDataFileStore
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.ElectionCalendar
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retention promised by the privacy policy (ARCHITECTURE.md 2.8, item 6): at every app start,
 * the cached lists of an election nobody opened for [MAX_IDLE] are deleted (candidates, offices
 * and their `fetch_state` rows), except the current election, whose lists must stay ready for
 * election day offline. Municipality lists and open-data copies not refreshed for as long go too.
 * "Opened" is the last refresh attempt of any list of the election, which every screen makes
 * when it opens (or the data was fetched within its TTL, at most a day earlier).
 */
@Singleton
class CacheRetention @Inject constructor(
    private val db: PublicCacheDatabase,
    private val policy: CachePolicy,
    private val openDataFiles: OpenDataFileStore,
) {
    private val electionDao = db.electionDao()
    private val municipalityDao = db.municipalityDao()
    private val officeDao = db.officeDao()
    private val candidateDao = db.candidateDao()
    private val fetchStateDao = db.fetchStateDao()

    /** A storage failure is returned, not thrown: the next start simply tries again. */
    suspend fun cleanUp(): AppResult<Unit> {
        val cutoff = policy.now().minus(MAX_IDLE)
        val cutoffMillis = cutoff.toEpochMilli()
        val generation = policy.cacheGeneration()
        val lists = storageResult {
            val states = fetchStateDao.getAll()
            val elections = electionDao.getAll().mapNotNull { it.toDomainOrNull() }
            val current = ElectionCalendar.currentElection(elections, policy.today())?.id
            val lastUse = HashMap<Long, Long>()
            for (state in states) {
                val electionId = FetchKeys.electionIdOf(state.fetchKey) ?: continue
                lastUse[electionId] = maxOf(lastUse[electionId] ?: 0L, state.lastUse())
            }
            val cachedElections = candidateDao.electionIds() + officeDao.electionIds() + lastUse.keys
            val expired = cachedElections.toSet().filter { it != current && (lastUse[it] ?: 0L) < cutoffMillis }
            val staleUfs = states.filter { it.lastUse() < cutoffMillis }.mapNotNull { FetchKeys.ufOf(it.fetchKey) }
            if (expired.isNotEmpty() || staleUfs.isNotEmpty()) {
                db.withTransaction {
                    if (!policy.isCurrent(generation)) return@withTransaction
                    for (electionId in expired) {
                        candidateDao.deleteElection(electionId)
                        officeDao.deleteElection(electionId)
                        fetchStateDao.deleteWithPrefix(FetchKeys.candidatesPrefix(electionId))
                        fetchStateDao.deleteWithPrefix(FetchKeys.officesPrefix(electionId))
                    }
                    for (uf in staleUfs) {
                        municipalityDao.deleteByUf(uf)
                        fetchStateDao.delete(FetchKeys.municipalities(uf))
                    }
                }
            }
        }
        val files = storageResult { openDataFiles.deleteUnusedSince(cutoff) }
        return lists as? AppResult.Failure ?: files
    }

    private fun FetchStateEntity.lastUse(): Long =
        lastAttemptAt ?: fetchedAt ?: 0L

    companion object {
        val MAX_IDLE: Duration = Duration.ofDays(60)
    }
}
