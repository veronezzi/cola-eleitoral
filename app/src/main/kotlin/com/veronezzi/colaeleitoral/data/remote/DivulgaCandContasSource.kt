package com.veronezzi.colaeleitoral.data.remote

import com.veronezzi.colaeleitoral.core.common.DefaultDispatcher
import com.veronezzi.colaeleitoral.core.network.TseJson
import com.veronezzi.colaeleitoral.data.mapper.toCandidateDetailOrNull
import com.veronezzi.colaeleitoral.data.mapper.toCandidateOrNull
import com.veronezzi.colaeleitoral.data.mapper.toElectionOrNull
import com.veronezzi.colaeleitoral.data.mapper.toMunicipalityOrNull
import com.veronezzi.colaeleitoral.data.mapper.toRemoteOfficeOrNull
import com.veronezzi.colaeleitoral.data.remote.api.DivulgaCandContasApi
import com.veronezzi.colaeleitoral.data.remote.dto.EleicaoListSerializer
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.Candidate
import com.veronezzi.colaeleitoral.domain.model.CandidateDetail
import com.veronezzi.colaeleitoral.domain.model.DataSource
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The DivulgaCandContas JSON API, primary source of every TSE record. Items without the fields
 * the domain needs are dropped instead of failing the list.
 */
@Singleton
class DivulgaCandContasSource @Inject constructor(
    private val api: DivulgaCandContasApi,
    private val executor: TseCallExecutor,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : CandidateRemoteSource {
    override val source: DataSource = DataSource.DIVULGA_CAND_CONTAS

    override fun supports(election: Election): Boolean = true

    /** E1: ordinary elections (`tipoEleicao = "O"`) with a known scope. */
    suspend fun fetchElections(): AppResult<List<Election>> = executor.execute {
        val array = api.ordinaryElections() as? JsonArray
            ?: throw SerializationException("eleicao/ordinarias is not a JSON array")
        withContext(defaultDispatcher) {
            TseJson.decodeFromJsonElement(EleicaoListSerializer, array).mapNotNull { it.toElectionOrNull() }
        }
    }

    /** E2: municipalities of [uf], listed under the latest municipal election. */
    suspend fun fetchMunicipalities(uf: String, municipalElectionId: Long): AppResult<List<ElectoralUnit>> =
        executor.execute {
            api.municipalities(uf, municipalElectionId).municipios
                .mapNotNull { it.toMunicipalityOrNull(uf) }
                .distinctBy { it.code }
        }

    /** E3: votable offices of [ueCode]. Vices and Senate alternates are dropped. */
    suspend fun fetchOffices(election: Election, ueCode: String): AppResult<List<RemoteOffice>> =
        executor.execute {
            api.offices(election.id, ueCode).cargos.mapNotNull { it.toRemoteOfficeOrNull() }.distinctBy { it.code }
        }

    /** E4. */
    override suspend fun fetchCandidates(
        election: Election,
        ueCode: String,
        officeCode: Int,
    ): AppResult<List<Candidate>> = executor.execute(retryOnBadRequest = true) {
        val response = api.candidates(election.year, ueCode, election.id, officeCode)
        withContext(defaultDispatcher) {
            response.candidatos
                .mapNotNull { it.toCandidateOrNull(election.id, ueCode, officeCode) }
                .distinctBy { it.id }
        }
    }

    /**
     * E5. [officeCodeHint] (from the cached list) is used when the detail omits `cargo`.
     * A 200 with an empty body (unpublished candidacy) is `NotFound`.
     */
    suspend fun fetchCandidateDetail(
        election: Election,
        ueCode: String,
        candidateId: Long,
        officeCodeHint: Int?,
    ): AppResult<CandidateDetail> = executor.execute(retryOnBadRequest = true) {
        api.candidate(election.year, ueCode, election.id, candidateId)
            .toCandidateDetailOrNull(election, ueCode, officeCodeHint)
            ?: throw SerializationException("candidatura/buscar: required fields missing")
    }
}
