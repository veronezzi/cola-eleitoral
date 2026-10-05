package com.veronezzi.colaeleitoral.data.remote

import com.veronezzi.colaeleitoral.data.remote.opendata.TseOpenDataSource
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.Candidate
import com.veronezzi.colaeleitoral.domain.model.Election
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the source of candidate lists. DivulgaCandContas first; when it answers
 * [AppError.Blocked] and the election is general, the TSE open data serves the list instead and
 * the result is tagged [com.veronezzi.colaeleitoral.domain.model.DataSource.TSE_OPEN_DATA].
 *
 * Once the open data has served a list after a refusal, the primary is not asked again for
 * [PRIMARY_COOLDOWN] (unless the user forces a refresh): every new request would only wait for
 * the same 403 and its retry. When the fallback fails too there is no cooldown (the primary is
 * then the only way to the data), and the error shown is the one the user can act on
 * ([mostUsefulError]).
 */
@Singleton
class CandidateSourceSelector @Inject constructor(
    private val primary: DivulgaCandContasSource,
    private val fallback: TseOpenDataSource,
    private val clock: Clock,
) {
    @Volatile
    private var primaryRefusedUntil: Instant = Instant.MIN

    @Volatile
    private var lastRefusal: AppError.Blocked? = null

    suspend fun fetchCandidates(
        election: Election,
        ueCode: String,
        officeCode: Int,
        force: Boolean,
    ): Sourced<List<Candidate>> {
        val canFallBack = fallback.supports(election)
        val skipPrimary = canFallBack && !force && clock.instant().isBefore(primaryRefusedUntil)
        val primaryResult = if (skipPrimary) {
            AppResult.Failure(lastRefusal ?: AppError.Blocked(null))
        } else {
            primary.fetchCandidates(election, ueCode, officeCode)
        }
        val refusal = (primaryResult as? AppResult.Failure)?.error as? AppError.Blocked
        if (refusal == null || !canFallBack) {
            if (!skipPrimary && primaryResult is AppResult.Success) reset()
            return Sourced(primaryResult, primary.source)
        }
        return when (val fallbackResult = fallback.fetchCandidates(election, ueCode, officeCode)) {
            is AppResult.Success -> {
                if (!skipPrimary) {
                    primaryRefusedUntil = clock.instant().plus(PRIMARY_COOLDOWN)
                    lastRefusal = refusal
                }
                Sourced(fallbackResult, fallback.source)
            }
            is AppResult.Failure -> {
                // No substitute: the next request asks the primary again.
                reset()
                Sourced(AppResult.Failure(mostUsefulError(refusal, fallbackResult.error)), primary.source)
            }
        }
    }

    /** Forgets a previous refusal ("Apagar dados baixados", network change). */
    fun reset() {
        primaryRefusedUntil = Instant.MIN
        lastRefusal = null
    }

    companion object {
        val PRIMARY_COOLDOWN: Duration = Duration.ofMinutes(5)

        /**
         * The primary refused and the fallback failed with [fallbackError]. A problem the user can
         * fix or should know about wins: no connection, a full disk, or the open-data layout changed
         * (only an app update fixes that). Otherwise the refusal stands: another network or the
         * official site may still work.
         */
        fun mostUsefulError(refusal: AppError.Blocked, fallbackError: AppError): AppError = when (fallbackError) {
            AppError.Network, AppError.Storage, AppError.Parsing -> fallbackError
            is AppError.Blocked, is AppError.Server, AppError.NotFound, is AppError.Unknown -> refusal
        }
    }
}
