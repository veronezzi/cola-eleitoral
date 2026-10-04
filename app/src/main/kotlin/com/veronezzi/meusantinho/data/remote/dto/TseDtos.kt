package com.veronezzi.meusantinho.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/*
 * DTOs of DivulgaCandContas (ARCHITECTURE.md 2.2 and 2.3), checked against the real responses in
 * app/src/test/resources/tse. Only mapped fields are declared: the API also sends CPF, título de
 * eleitor, birth date, e-mails, race, gender identity, assets and more, and NONE of that may be
 * declared here. With ignoreUnknownKeys those keys are dropped while parsing.
 */

/** E1 `eleicao/ordinarias` item. */
@Serializable
data class EleicaoDto(
    val id: Long? = null,
    val ano: Int? = null,
    val nomeEleicao: String? = null,
    val tipoEleicao: String? = null,
    val tipoAbrangencia: String? = null,
    val dataEleicao: String? = null,
    val turno: JsonPrimitive? = null,
)

/** Electoral unit: country ("BR"), UF ("AC") or municipality ("01120"). */
@Serializable
data class UeDto(
    val codigo: String? = null,
    val nome: String? = null,
    val sigla: String? = null,
)

/** E2 `eleicao/buscar/{uf}/{electionId}/municipios`. */
@Serializable
data class MunicipiosResponseDto(
    val estado: UeDto? = null,
    @Serializable(with = UeListSerializer::class)
    val municipios: List<UeDto> = emptyList(),
)

/** Office (cargo). `codSuperior` and `titular` are inconsistent across routes and not declared. */
@Serializable
data class CargoDto(
    val codigo: Int? = null,
    val nome: String? = null,
    val contagem: Int? = null,
)

/** E3 `eleicao/listar/municipios/{electionId}/{ueCode}/cargos`. */
@Serializable
data class CargosResponseDto(
    @SerialName("unidadeEleitoralDTO")
    val unidadeEleitoral: UeDto? = null,
    @Serializable(with = CargoListSerializer::class)
    val cargos: List<CargoDto> = emptyList(),
)

/** E4 `candidatura/listar/{year}/{ueCode}/{electionId}/{officeCode}/candidatos`. */
@Serializable
data class CandidatosResponseDto(
    val unidadeEleitoral: UeDto? = null,
    val cargo: CargoDto? = null,
    @Serializable(with = CandidatoListSerializer::class)
    val candidatos: List<CandidatoDto> = emptyList(),
)

@Serializable
data class PartidoDto(
    val numero: Int? = null,
    val sigla: String? = null,
    val nome: String? = null,
)

/** Candidacy, in E4 lists and in the E5 detail (`candidatura/buscar/.../candidato/{id}`). */
@Serializable
data class CandidatoDto(
    val id: Long? = null,
    val numero: Int? = null,
    val nomeUrna: String? = null,
    val nomeCompleto: String? = null,
    val partido: PartidoDto? = null,
    val nomeColigacao: String? = null,
    val composicaoColigacao: String? = null,
    val descricaoTipoDrap: String? = null,
    val descricaoSituacao: String? = null,
    val descricaoTotalizacao: String? = null,
    val descricaoSituacaoCandidato: String? = null,
    val candidatoApto: Boolean? = null,
    val cargo: CargoDto? = null,
    val ufCandidatura: String? = null,
    val ufSuperiorCandidatura: String? = null,
    val fotoUrl: String? = null,
    val fotoUrlPublicavel: Boolean? = null,
    /** "yyyy-MM-dd HH:mm" since 2024; epoch millis in older schemas. */
    val dataUltimaAtualizacao: JsonPrimitive? = null,
    @Serializable(with = ViceListSerializer::class)
    val vices: List<ViceDto> = emptyList(),
    @Serializable(with = EleicaoAnteriorListSerializer::class)
    val eleicoesAnteriores: List<EleicaoAnteriorDto> = emptyList(),
    val eleicao: EleicaoRefDto? = null,
)

/** Vice or Senate alternate (`vices[]` in the detail). The API uses upper-snake keys here. */
@Serializable
data class ViceDto(
    @SerialName("sq_CANDIDATO")
    val sqCandidato: Long? = null,
    @SerialName("nm_URNA")
    val nmUrna: String? = null,
    @SerialName("nm_CANDIDATO")
    val nmCandidato: String? = null,
    @SerialName("ds_CARGO")
    val dsCargo: String? = null,
    @SerialName("sg_PARTIDO")
    val sgPartido: String? = null,
    val urlFoto: String? = null,
    val urlFotoPublicavel: Boolean? = null,
    val situacaoCandidato: String? = null,
    /** 3 = replaced running mate (not current). */
    val situacaoVice: Int? = null,
)

/** `eleicoesAnteriores[]`: the entry whose [id] is the candidate itself carries the official link. */
@Serializable
data class EleicaoAnteriorDto(
    val id: String? = null,
    val txLink: String? = null,
)

@Serializable
data class EleicaoRefDto(
    val id: Long? = null,
)
