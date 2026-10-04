package com.veronezzi.colaeleitoral.data.remote

import com.veronezzi.colaeleitoral.core.network.DisallowedHostException
import com.veronezzi.colaeleitoral.core.network.TseBlockedException
import com.veronezzi.colaeleitoral.core.network.TseEmptyBodyException
import com.veronezzi.colaeleitoral.core.network.TseHttpStatusException
import com.veronezzi.colaeleitoral.core.network.TseUnusableResponseException
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Retry rules of ARCHITECTURE.md 2.6: up to [maxAttempts] for network failures, 5xx, 408, 429
 * (honoring `Retry-After` up to [maxRetryAfter]) and transient 400s, waiting [backoff] plus up to
 * [maxJitter]; a single retry after [blockedRetryDelay] for 403 and HTML challenges.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val backoff: List<Duration> = listOf(1.seconds, 3.seconds),
    val maxJitter: Duration = 250.milliseconds,
    val blockedRetryDelay: Duration = 2.seconds,
    val maxRetryAfter: Duration = 10.seconds,
)

/**
 * Runs one TSE request under [RetryPolicy] and maps every failure to [AppError]. Waits use
 * coroutine `delay` (cancellable; virtual time in tests). Errors never carry response bodies or
 * parser messages: both may contain CPF and título of candidates.
 */
class TseCallExecutor(
    private val policy: RetryPolicy = RetryPolicy(),
    private val random: Random = Random.Default,
) {
    /** @param retryOnBadRequest retry HTTP 400 too (transient on `candidatura/` routes). */
    suspend fun <T> execute(retryOnBadRequest: Boolean = false, call: suspend () -> T): AppResult<T> {
        var attempt = 0
        var blockedRetryUsed = false
        while (true) {
            attempt++
            val outcome = try {
                return AppResult.Success(call())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                classify(e, attempt, retryOnBadRequest)
            }
            val error = outcome.error
            val isRefusal = error is AppError.Blocked && error.httpCode != HTTP_TOO_MANY_REQUESTS
            val wait = outcome.retryAfter
            if (wait == null || attempt >= policy.maxAttempts || (isRefusal && blockedRetryUsed)) {
                return AppResult.Failure(error)
            }
            if (isRefusal) blockedRetryUsed = true
            delay(wait)
        }
    }

    private class Outcome(val error: AppError, val retryAfter: Duration?)

    private fun classify(e: Exception, attempt: Int, retryOnBadRequest: Boolean): Outcome = when (e) {
        is TseBlockedException ->
            if (e.httpCode == HTTP_TOO_MANY_REQUESTS) {
                val wait = e.retryAfterSeconds?.seconds ?: backoff(attempt)
                Outcome(AppError.Blocked(HTTP_TOO_MANY_REQUESTS), wait.takeIf { it <= policy.maxRetryAfter })
            } else {
                Outcome(AppError.Blocked(e.httpCode), policy.blockedRetryDelay)
            }
        is TseEmptyBodyException -> Outcome(AppError.NotFound, null)
        is DisallowedHostException, is TseUnusableResponseException -> Outcome(AppError.Unknown(null), null)
        is HttpException -> httpOutcome(e.code(), attempt, retryOnBadRequest)
        is TseHttpStatusException -> httpOutcome(e.httpCode, attempt, retryOnBadRequest)
        is SerializationException -> Outcome(AppError.Parsing, null)
        is IOException -> Outcome(AppError.Network, backoff(attempt))
        else -> Outcome(AppError.Unknown(e), null)
    }

    private fun httpOutcome(code: Int, attempt: Int, retryOnBadRequest: Boolean): Outcome = when {
        code == HTTP_NOT_FOUND || code == HTTP_GONE -> Outcome(AppError.NotFound, null)
        code == HTTP_FORBIDDEN -> Outcome(AppError.Blocked(code), policy.blockedRetryDelay)
        code == HTTP_TOO_MANY_REQUESTS -> Outcome(AppError.Blocked(code), backoff(attempt))
        code >= HTTP_SERVER_ERROR || code == HTTP_REQUEST_TIMEOUT -> Outcome(AppError.Server(code), backoff(attempt))
        code == HTTP_BAD_REQUEST && retryOnBadRequest -> Outcome(AppError.Server(code), backoff(attempt))
        else -> Outcome(AppError.Unknown(TseHttpStatusException(code)), null)
    }

    private fun backoff(attempt: Int): Duration {
        val base = policy.backoff.getOrElse(attempt - 1) { policy.backoff.last() }
        val jitter = random.nextLong(policy.maxJitter.inWholeMilliseconds + 1).milliseconds
        return base + jitter
    }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_GONE = 410
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
    }
}
