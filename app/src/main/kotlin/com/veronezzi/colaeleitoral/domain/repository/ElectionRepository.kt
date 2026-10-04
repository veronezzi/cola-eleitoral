package com.veronezzi.colaeleitoral.domain.repository

import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.CachedData
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import com.veronezzi.colaeleitoral.domain.model.Office
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import kotlinx.coroutines.flow.Flow

/**
 * Elections, municipalities and offices. Reads emit the cache immediately; `refresh*` downloads
 * only when the cache is older than its TTL, unless [force] is set (pull-to-refresh).
 */
interface ElectionRepository {
    /** Ordinary elections (TSE `tipoEleicao = "O"`), newest first. */
    fun observeElections(): Flow<CachedData<List<Election>>>

    suspend fun refreshElections(force: Boolean = false): AppResult<Unit>

    /** Municipalities of [uf], taken from the most recent municipal election the TSE lists. */
    fun observeMunicipalities(uf: String): Flow<CachedData<List<ElectoralUnit>>>

    suspend fun refreshMunicipalities(uf: String, force: Boolean = false): AppResult<Unit>

    /**
     * Votable offices of [election] for [location] in [round], in urna order. Falls back to the
     * law-defined offices when the TSE office list is unavailable, so the ballot always renders.
     */
    fun observeBallotOffices(
        election: Election,
        location: VoterLocation,
        round: Round,
    ): Flow<CachedData<List<Office>>>

    suspend fun refreshBallotOffices(
        election: Election,
        location: VoterLocation,
        force: Boolean = false,
    ): AppResult<Unit>

    /** Deletes every downloaded public record (elections, municipalities, offices, candidates). */
    suspend fun clearCache()
}
