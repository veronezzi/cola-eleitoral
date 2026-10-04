package com.veronezzi.meusantinho.data.repository

import com.veronezzi.meusantinho.data.local.db.FetchStateEntity
import com.veronezzi.meusantinho.data.mapper.AppErrorCodec
import com.veronezzi.meusantinho.data.mapper.dataSourceOf
import com.veronezzi.meusantinho.domain.model.AppError
import com.veronezzi.meusantinho.domain.model.AppResult
import com.veronezzi.meusantinho.domain.model.CachedData
import com.veronezzi.meusantinho.domain.model.DataSource
import com.veronezzi.meusantinho.domain.model.Election
import com.veronezzi.meusantinho.domain.model.ElectionCalendar
import com.veronezzi.meusantinho.domain.model.Round
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** Cached data kinds and their TTLs (ARCHITECTURE.md 2.8): normal and election week. */
enum class CacheKind(val normalTtl: Duration, val electionWeekTtl: Duration) {
    ELECTIONS(Duration.ofHours(24), Duration.ofHours(6)),
    MUNICIPALITIES(Duration.ofDays(30), Duration.ofDays(30)),
    OFFICES(Duration.ofHours(24), Duration.ofHours(6)),
    CANDIDATES(Duration.ofHours(6), Duration.ofHours(1)),
    DETAIL(Duration.ofHours(6), Duration.ofHours(1)),
}

/** Keys of `fetch_state`. */
object FetchKeys {
    const val ELECTIONS = "elections"

    fun municipalities(uf: String) = "municipalities:$uf"

    fun offices(electionId: Long, ueCode: String) = "offices:$electionId:$ueCode"

    fun candidates(electionId: Long, ueCode: String, officeCode: Int) = "candidates:$electionId:$ueCode:$officeCode"

    fun detail(electionId: Long, candidateId: Long) = "detail:$electionId:$candidateId"
}

/**
 * Freshness rules with an injected clock. "Election week" is D-7 to D+1 of each round, in
 * Brasília. A key is refreshed at most once per [MIN_REFRESH_INTERVAL], forced or not, so neither
 * a pull-to-refresh nor a failing screen can hammer the TSE.
 */
class CachePolicy(private val clock: Clock) {
    fun now(): Instant = clock.instant()

    // atZone instead of LocalDate.ofInstant: the latter needs API 34 (minSdk is 26).
    fun today(): LocalDate = clock.instant().atZone(ElectionCalendar.BRASILIA).toLocalDate()

    fun isElectionWeek(elections: Iterable<Election>): Boolean {
        val today = today()
        return elections.any { election ->
            Round.entries.mapNotNull { election.dateOf(it) }.any { day ->
                !today.isBefore(day.minusDays(ELECTION_WEEK_DAYS_BEFORE)) && !today.isAfter(day.plusDays(1))
            }
        }
    }

    fun ttl(kind: CacheKind, electionWeek: Boolean): Duration = if (electionWeek) kind.electionWeekTtl else kind.normalTtl

    /** Older than [ttl], never fetched, or the last refresh failed. */
    fun isStale(state: FetchStateEntity?, ttl: Duration): Boolean {
        val fetchedAt = state?.fetchedAt ?: return true
        return state.lastError != null || Duration.between(Instant.ofEpochMilli(fetchedAt), now()) > ttl
    }

    /** The result to return without any download, or null when the key must be fetched now. */
    fun resultWithoutFetch(state: FetchStateEntity?, ttl: Duration, force: Boolean): AppResult<Unit>? {
        val lastAttempt = state?.lastAttemptAt?.let(Instant::ofEpochMilli)
        if (lastAttempt != null && Duration.between(lastAttempt, now()) < MIN_REFRESH_INTERVAL) {
            return state.lastError?.let { AppResult.Failure(AppErrorCodec.decode(it)) } ?: AppResult.Success(Unit)
        }
        return if (!force && !isStale(state, ttl)) AppResult.Success(Unit) else null
    }

    fun success(key: String, source: DataSource): FetchStateEntity {
        val now = now().toEpochMilli()
        return FetchStateEntity(fetchKey = key, fetchedAt = now, lastAttemptAt = now, lastError = null, source = source.name)
    }

    /** Keeps the previous success time and source: the cached rows are still there. */
    fun failure(key: String, previous: FetchStateEntity?, error: AppError) = FetchStateEntity(
        fetchKey = key,
        fetchedAt = previous?.fetchedAt,
        lastAttemptAt = now().toEpochMilli(),
        lastError = AppErrorCodec.encode(error),
        source = previous?.source,
    )

    fun <T> cachedData(value: T, state: FetchStateEntity?, ttl: Duration): CachedData<T> = CachedData(
        value = value,
        fetchedAt = state?.fetchedAt?.let(Instant::ofEpochMilli),
        isStale = isStale(state, ttl),
        lastError = state?.lastError?.let(AppErrorCodec::decode),
        source = dataSourceOf(state?.source),
    )

    companion object {
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofMinutes(1)
        private const val ELECTION_WEEK_DAYS_BEFORE = 7L
    }
}

/** One mutex per key: concurrent refreshes of the same key run one after the other. */
class KeyedMutex {
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(key: String, block: suspend () -> T): T =
        mutexes.computeIfAbsent(key) { Mutex() }.withLock { block() }
}
