package com.veronezzi.colaeleitoral.data.repository

import com.veronezzi.colaeleitoral.data.testing.MutableClock
import com.veronezzi.colaeleitoral.data.testing.TestElections
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.DataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

/** Refresh limit (A3): only outcomes that reached the TSE wait a minute, and a new network lifts it. */
class CachePolicyTest {
    private val clock = MutableClock(TestElections.ELECTION_DAY_MORNING)
    private val policy = CachePolicy(clock)
    private val ttl = Duration.ofHours(1)

    private fun failedJustNow(error: AppError) = policy.failure(KEY, policy.success(KEY, DataSource.DIVULGA_CAND_CONTAS), error)

    @Test
    fun networkErrorWithForceFetchesAtOnce() {
        assertNull(policy.resultWithoutFetch(failedJustNow(AppError.Network), ttl, force = true))
        assertNull("stale after a failure, so even without force", policy.resultWithoutFetch(failedJustNow(AppError.Network), ttl, force = false))
    }

    @Test
    fun failuresThatNeverReachedTheTseAreNotLimited() {
        for (error in listOf(AppError.Network, AppError.Storage, AppError.Unknown(null))) {
            assertNull(error.toString(), policy.resultWithoutFetch(failedJustNow(error), ttl, force = true))
        }
    }

    @Test
    fun answersFromTheTseWaitAMinuteEvenWhenForced() {
        for (error in listOf(AppError.Blocked(403), AppError.Server(503), AppError.NotFound, AppError.Parsing)) {
            assertEquals(error.toString(), AppResult.Failure(error), policy.resultWithoutFetch(failedJustNow(error), ttl, force = true))
        }
        val success = policy.success(KEY, DataSource.DIVULGA_CAND_CONTAS)
        assertEquals(AppResult.Success(Unit), policy.resultWithoutFetch(success, ttl, force = true))

        clock.advance(CachePolicy.MIN_REFRESH_INTERVAL)
        assertNull(policy.resultWithoutFetch(success, ttl, force = true))
    }

    @Test
    fun aNetworkChangeLiftsTheLimitOfFailedKeysOnly() {
        val refused = failedJustNow(AppError.Blocked(403))
        val success = policy.success(KEY, DataSource.DIVULGA_CAND_CONTAS)

        policy.forgiveFailures()

        assertNull(policy.resultWithoutFetch(refused, ttl, force = true))
        assertEquals("fresh data needs no new download", AppResult.Success(Unit), policy.resultWithoutFetch(success, ttl, force = true))
        clock.advance(Duration.ofSeconds(1))
        val refusedAgain = failedJustNow(AppError.Blocked(403))
        assertEquals(AppResult.Failure(AppError.Blocked(403)), policy.resultWithoutFetch(refusedAgain, ttl, force = true))
    }

    @Test
    fun clearingTheCacheInvalidatesRefreshesInFlight() {
        val generation = policy.cacheGeneration()

        policy.onCacheCleared()

        assertEquals(false, policy.isCurrent(generation))
        assertEquals(true, policy.isCurrent(policy.cacheGeneration()))
    }

    @Test
    fun fetchKeysNameTheirElectionOrUf() {
        assertEquals(20322002026L, FetchKeys.electionIdOf(FetchKeys.candidates(20322002026L, "SP", 6)))
        assertEquals(2045202024L, FetchKeys.electionIdOf(FetchKeys.offices(2045202024L, "81809")))
        assertNull(FetchKeys.electionIdOf(FetchKeys.ELECTIONS))
        assertEquals("SP", FetchKeys.ufOf(FetchKeys.municipalities("SP")))
        assertNull(FetchKeys.ufOf(FetchKeys.offices(1, "SP")))
    }

    private companion object {
        const val KEY = "candidates:20322002026:BR:1"
    }
}
