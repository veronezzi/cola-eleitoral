package com.veronezzi.colaeleitoral.data.repository

import com.veronezzi.colaeleitoral.data.local.db.FetchStateEntity
import com.veronezzi.colaeleitoral.data.mapper.AppErrorCodec
import com.veronezzi.colaeleitoral.data.mapper.dataSourceOf
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.CachedData
import com.veronezzi.colaeleitoral.domain.model.DataSource
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectionCalendar
import com.veronezzi.colaeleitoral.domain.model.Round
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Cached data kinds and their TTLs (ARCHITECTURE.md 2.8): normal and election week. */
enum class CacheKind(val normalTtl: Duration, val electionWeekTtl: Duration) {
    ELECTIONS(Duration.ofHours(24), Duration.ofHours(6)),
    MUNICIPALITIES(Duration.ofDays(30), Duration.ofDays(30)),
    OFFICES(Duration.ofHours(24), Duration.ofHours(6)),
    CANDIDATES(Duration.ofHours(6), Duration.ofHours(1)),
    DETAIL(Duration.ofHours(6), Duration.ofHours(1)),
}

/** Keys of `fetch_state`, plus the in-memory key of a candidate detail (never stored). */
object FetchKeys {
    const val ELECTIONS = "elections"
    private const val MUNICIPALITIES_PREFIX = "municipalities:"

    fun municipalities(uf: String) = "$MUNICIPALITIES_PREFIX$uf"

    fun offices(electionId: Long, ueCode: String) = "${officesPrefix(electionId)}$ueCode"

    fun candidates(electionId: Long, ueCode: String, officeCode: Int) = "${candidatesPrefix(electionId)}$ueCode:$officeCode"

    /** Key of the in-memory detail cache: details are never written to `fetch_state` (S2). */
    fun detail(electionId: Long, candidateId: Long) = "detail:$electionId:$candidateId"

    fun officesPrefix(electionId: Long) = "offices:$electionId:"

    fun candidatesPrefix(electionId: Long) = "candidates:$electionId:"

    /** The election of an `offices:` or `candidates:` key, or null for any other key. */
    fun electionIdOf(key: String): Long? {
        val parts = key.split(':')
        if (parts.size < 2 || (parts[0] != "offices" && parts[0] != "candidates")) return null
        return parts[1].toLongOrNull()
    }

    /** The UF of a `municipalities:` key, or null for any other key. */
    fun ufOf(key: String): String? = key.takeIf { it.startsWith(MUNICIPALITIES_PREFIX) }?.removePrefix(MUNICIPALITIES_PREFIX)
}

/**
 * Freshness rules with an injected clock, shared by every repository (one instance). "Election
 * week" is D-7 to D+1 of each round, in Brasília.
 *
 * Refresh limit: a key is downloaded at most once per [MIN_REFRESH_INTERVAL], forced or not,
 * after a success or a failure that reached the TSE (refusal, 5xx, not found, unexpected
 * format), so neither a pull-to-refresh nor a failing screen can hammer it. A failure that never
 * reached the TSE (no connection, local storage, unknown) may be retried at once, and a change of
 * network ([forgiveFailures], called by the `NetworkMonitor`) lifts the limit of every failed key:
 * a refusal of one network says nothing about the next one.
 */
@Singleton
class CachePolicy @Inject constructor(private val clock: Clock) {
    @Volatile
    private var failuresForgivenAt: Instant = Instant.MIN
    private val generation = AtomicLong()

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
        val lastError = state?.lastError?.let(AppErrorCodec::decode)
        val limited = lastAttempt != null &&
            Duration.between(lastAttempt, now()) < MIN_REFRESH_INTERVAL &&
            (lastError == null || (lastError.reachedTheTse() && lastAttempt.isAfter(failuresForgivenAt)))
        if (limited) return lastError?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit)
        return if (!force && !isStale(state, ttl)) AppResult.Success(Unit) else null
    }

    /** The default network changed: failed keys may be downloaded again right away. */
    fun forgiveFailures() {
        failuresForgivenAt = now()
    }

    /**
     * Token of the current cache contents. [onCacheCleared] changes it, so a refresh that started
     * before "Limpar dados baixados" or "Apagar meus dados" checks [isCurrent] inside its write
     * transaction and drops its result instead of writing it back.
     */
    fun cacheGeneration(): Long = generation.get()

    fun isCurrent(cacheGeneration: Long): Boolean = generation.get() == cacheGeneration

    fun onCacheCleared() {
        generation.incrementAndGet()
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

        /** Whether the request got an answer from the TSE (so repeating it at once changes nothing). */
        private fun AppError.reachedTheTse(): Boolean = when (this) {
            is AppError.Blocked, is AppError.Server, AppError.NotFound, AppError.Parsing -> true
            AppError.Network, AppError.Storage, is AppError.Unknown -> false
        }
    }
}

/** One mutex per key: concurrent refreshes of the same key run one after the other. */
class KeyedMutex {
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(key: String, block: suspend () -> T): T =
        mutexes.computeIfAbsent(key) { Mutex() }.withLock { block() }
}
