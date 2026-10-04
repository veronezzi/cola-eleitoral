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
 * After a refusal the primary is not asked again for [PRIMARY_COOLDOWN] (unless the user forces
 * a refresh): every new request would only wait for the same 403 and its retry.
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
            if (!skipPrimary && primaryResult is AppResult.Success) primaryRefusedUntil = Instant.MIN
            return Sourced(primaryResult, primary.source)
        }
        if (!skipPrimary) {
            primaryRefusedUntil = clock.instant().plus(PRIMARY_COOLDOWN)
            lastRefusal = refusal
        }
        return when (val fallbackResult = fallback.fetchCandidates(election, ueCode, officeCode)) {
            is AppResult.Success -> Sourced(fallbackResult, fallback.source)
            // Both failed: report why the official API failed (the UI links to the TSE site).
            is AppResult.Failure -> Sourced(primaryResult, primary.source)
        }
    }

    /** Forgets a previous refusal ("Apagar dados baixados", network change). */
    fun reset() {
        primaryRefusedUntil = Instant.MIN
        lastRefusal = null
    }

    companion object {
        val PRIMARY_COOLDOWN: Duration = Duration.ofMinutes(5)
    }
}
