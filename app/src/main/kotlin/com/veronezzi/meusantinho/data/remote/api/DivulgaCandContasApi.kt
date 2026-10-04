package com.veronezzi.meusantinho.data.remote.api

import com.veronezzi.meusantinho.data.remote.dto.CandidatoDto
import com.veronezzi.meusantinho.data.remote.dto.CandidatosResponseDto
import com.veronezzi.meusantinho.data.remote.dto.CargosResponseDto
import com.veronezzi.meusantinho.data.remote.dto.MunicipiosResponseDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * DivulgaCandContas endpoints used by the app (ARCHITECTURE.md 2.2), relative to
 * `https://divulgacandcontas.tse.jus.br/divulga/rest/v1/`. Read-only, no authentication.
 */
interface DivulgaCandContasApi {
    /** E1. A JSON array, decoded item by item so one bad entry does not drop the list. */
    @GET("eleicao/ordinarias")
    suspend fun ordinaryElections(): JsonElement

    /** E2. Use the id of the latest municipal election. */
    @GET("eleicao/buscar/{uf}/{electionId}/municipios")
    suspend fun municipalities(
        @Path("uf") uf: String,
        @Path("electionId") electionId: Long,
    ): MunicipiosResponseDto

    /** E3. [ueCode] is "BR", a UF or a municipality code. */
    @GET("eleicao/listar/municipios/{electionId}/{ueCode}/cargos")
    suspend fun offices(
        @Path("electionId") electionId: Long,
        @Path("ueCode") ueCode: String,
    ): CargosResponseDto

    /** E4. */
    @GET("candidatura/listar/{year}/{ueCode}/{electionId}/{officeCode}/candidatos")
    suspend fun candidates(
        @Path("year") year: Int,
        @Path("ueCode") ueCode: String,
        @Path("electionId") electionId: Long,
        @Path("officeCode") officeCode: Int,
    ): CandidatosResponseDto

    /** E5. Unpublished candidacies answer 200 with an empty body (mapped to NotFound). */
    @GET("candidatura/buscar/{year}/{ueCode}/{electionId}/candidato/{candidateId}")
    suspend fun candidate(
        @Path("year") year: Int,
        @Path("ueCode") ueCode: String,
        @Path("electionId") electionId: Long,
        @Path("candidateId") candidateId: Long,
    ): CandidatoDto
}
