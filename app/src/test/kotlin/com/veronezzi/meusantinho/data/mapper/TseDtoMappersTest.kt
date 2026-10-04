package com.veronezzi.meusantinho.data.mapper

import com.veronezzi.meusantinho.core.network.TseJson
import com.veronezzi.meusantinho.data.local.db.CandidateDetailEntity
import com.veronezzi.meusantinho.data.remote.dto.CandidatoDto
import com.veronezzi.meusantinho.data.remote.dto.CandidatosResponseDto
import com.veronezzi.meusantinho.data.remote.dto.CargoDto
import com.veronezzi.meusantinho.data.remote.dto.EleicaoListSerializer
import com.veronezzi.meusantinho.data.remote.dto.MunicipiosResponseDto
import com.veronezzi.meusantinho.data.testing.Fixtures
import com.veronezzi.meusantinho.data.testing.TestElections
import com.veronezzi.meusantinho.domain.model.AppError
import com.veronezzi.meusantinho.domain.model.ElectionScope
import com.veronezzi.meusantinho.domain.model.RunningMate
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TseDtoMappersTest {
    @Test
    fun electionsMapWithScopeDateAndRound() {
        val array = TseJson.parseToJsonElement(Fixtures.text("tse/eleicoes-ordinarias.json")).jsonArray
        val elections = TseJson.decodeFromJsonElement(EleicaoListSerializer, array).mapNotNull { it.toElectionOrNull() }

        assertEquals(13, elections.size)
        val current = elections.first()
        assertEquals(TestElections.GENERAL_2026, current)
        assertEquals(LocalDate.of(2026, 10, 25), current.secondRoundDate)
        assertEquals(ElectionScope.MUNICIPAL, elections.single { it.id == 2045202024L }.scope)
        assertNull(elections.single { it.id == 14417L }.date)
        assertTrue(elections.all { it.round == null })
    }

    @Test
    fun electionsWithUnknownTypeOrScopeAreDropped() {
        val json = """[{"id":1,"ano":2026,"tipoEleicao":"S","tipoAbrangencia":"F"},
            {"id":2,"ano":2026,"tipoEleicao":"O","tipoAbrangencia":"X"},
            {"id":3,"tipoEleicao":"O","tipoAbrangencia":"M"},
            {"id":4,"ano":2028,"tipoEleicao":"O","tipoAbrangencia":"M","dataEleicao":"2028-10-01","turno":2}]"""

        val elections = TseJson.decodeFromString(EleicaoListSerializer, json).mapNotNull { it.toElectionOrNull() }

        assertEquals(listOf(4L), elections.map { it.id })
        assertEquals("Eleições 2028", elections.single().name)
        assertEquals(2, elections.single().round?.number)
    }

    @Test
    fun municipalitiesTakeTheQueriedUf() {
        val dto = TseJson.decodeFromString(MunicipiosResponseDto.serializer(), Fixtures.text("tse/municipios.json"))

        val acrelandia = dto.municipios.mapNotNull { it.toMunicipalityOrNull("AC") }.single { it.code == "01120" }

        assertEquals("ACRELÂNDIA", acrelandia.name)
        assertEquals("AC", acrelandia.uf)
        assertTrue(acrelandia.isMunicipality)
    }

    @Test
    fun onlyVotableOfficesAreKept() {
        val offices = listOf(CargoDto(3, "Governador", 7), CargoDto(4, "Vice-governador", 7), CargoDto(9, "1º Suplente", 9), CargoDto(5, null, 16))
            .mapNotNull { it.toRemoteOfficeOrNull() }

        assertEquals(listOf(3, 5), offices.map { it.code })
        assertEquals("Senador", offices.last().name)
        assertEquals(7, offices.first().candidateCount)
    }

    @Test
    fun listItemsGetPartyNumberAndPhotoUrl() {
        val dto = TseJson.decodeFromString(CandidatosResponseDto.serializer(), Fixtures.text("tse/candidatos-listar.json"))

        val candidates = dto.candidatos.mapNotNull { it.toCandidateOrNull(2040602022, "BR", 1) }

        assertEquals(13, candidates.size)
        val ciro = candidates.single { it.id == 280001612393L }
        assertEquals(12, ciro.number)
        assertEquals("PDT", ciro.party.acronym)
        assertEquals(12, ciro.party.number)
        assertEquals("PDT", ciro.coalition)
        assertEquals("Deferido", ciro.status.registration)
        assertEquals("Concorrendo", ciro.status.totalization)
        assertEquals(
            "https://divulgacandcontas.tse.jus.br/divulga/rest/arquivo/img/2040602022/280001612393/BR",
            ciro.photoUrl,
        )
        assertEquals("Indeferido", candidates.single { it.id == 280001655452L }.status.registration)
        assertEquals("Cancelado", candidates.single { it.id == 280001600179L }.status.registration)
        assertEquals(2, candidates.count { it.number == 14 })
    }

    @Test
    fun itemsOfAnotherOfficeAreDropped() {
        val dto = TseJson.decodeFromString(CandidatosResponseDto.serializer(), Fixtures.text("tse/candidatos-listar.json"))

        assertTrue(dto.candidatos.mapNotNull { it.toCandidateOrNull(2040602022, "BR", 3) }.isEmpty())
    }

    @Test
    fun detailOf2026MapsStatusLinkAndUpdateTime() {
        val dto = TseJson.decodeFromString(CandidatoDto.serializer(), Fixtures.text("tse/candidato-buscar.json"))

        val detail = requireNotNull(dto.toCandidateDetailOrNull(TestElections.GENERAL_2026, "SP", officeCodeHint = null))

        with(detail.candidate) {
            assertEquals(5070, number)
            assertEquals(6, officeCode)
            assertEquals("PSOL", party.acronym)
            assertEquals(50, party.number)
            assertEquals("PARTIDO SOCIALISMO E LIBERDADE", party.name)
            assertEquals("FEDERAÇÃO PSOL REDE(50-PSOL/18-REDE)", coalition)
            assertEquals("Deferido", status.registration)
            assertEquals("Consta da urna", status.onBallot)
            assertEquals(true, status.isFit)
            assertEquals("https://divulgacandcontas.tse.jus.br/divulga/rest/arquivo/img/20322002026/250002539612/SP", photoUrl)
        }
        assertEquals("Federação", detail.coalitionType)
        assertNull(detail.coalitionComposition)
        assertTrue(detail.runningMates.isEmpty())
        assertEquals(
            "https://divulgacandcontas.tse.jus.br/divulga/#/candidato/2026/20322002026/SP/250002539612",
            detail.officialPageUrl,
        )
        assertEquals(true, detail.photoPublishable)
        assertEquals(LocalDateTime.of(2026, 9, 18, 14, 39), detail.lastUpdate)
    }

    @Test
    fun municipalDetailMapsTheRunningMate() {
        val dto = TseJson.decodeFromString(CandidatoDto.serializer(), Fixtures.text("tse/candidato-buscar-com-vice.json"))

        val detail = requireNotNull(dto.toCandidateDetailOrNull(TestElections.MUNICIPAL_2024, "81809", officeCodeHint = null))

        assertEquals("Aguardando julgamento", detail.candidate.status.registration)
        assertEquals("Cadastrado", detail.candidate.status.onBallot)
        assertEquals(false, detail.candidate.status.isFit)
        assertEquals(30, detail.candidate.party.number)
        assertEquals("Partido Isolado", detail.coalitionType)
        assertEquals(
            listOf(
                RunningMate(
                    id = 240001918431,
                    role = "Vice-prefeito",
                    ballotName = "SILVESTRE",
                    fullName = "SILVESTRE ETGES",
                    partyAcronym = "NOVO",
                    photoUrl = "https://divulgacandcontas.tse.jus.br/divulga/rest/arquivo/img/2045202024/240001918431/81809",
                    status = null,
                ),
            ),
            detail.runningMates,
        )
        assertEquals(
            "https://divulgacandcontas.tse.jus.br/divulga/#/candidato/2024/2045202024/81809/240001918430",
            detail.officialPageUrl,
        )
    }

    @Test
    fun replacedRunningMatesAndUnpublishablePhotosAreHidden() {
        val json = """{"id":1,"numero":30,"nomeUrna":"A","partido":{"sigla":"NOVO"},"cargo":{"codigo":11},
            "fotoUrl":"https://divulgacandcontas.tse.jus.br/divulga/rest/arquivo/img/1/1/81809","fotoUrlPublicavel":false,
            "vices":[{"sq_CANDIDATO":2,"nm_URNA":"OLD","situacaoVice":3},{"sq_CANDIDATO":3,"nm_URNA":"NEW",
            "urlFoto":"https://example.com/x.png"}]}"""
        val dto = TseJson.decodeFromString(CandidatoDto.serializer(), json)

        val detail = requireNotNull(dto.toCandidateDetailOrNull(TestElections.MUNICIPAL_2024, "81809", null))

        assertNull(detail.candidate.photoUrl)
        assertEquals(false, detail.photoPublishable)
        assertEquals(listOf("NEW"), detail.runningMates.map { it.ballotName })
        assertEquals("Vice-prefeito", detail.runningMates.single().role)
        assertNull("only TSE photo URLs are used", detail.runningMates.single().photoUrl)
        assertEquals("https://divulgacandcontas.tse.jus.br/divulga/", detail.officialPageUrl)
    }

    @Test
    fun officialPageFallsBackToTheDocumentedFormat() {
        assertEquals(
            "https://divulgacandcontas.tse.jus.br/divulga/#/candidato/SP/SP/20322002026/42/2026/SP",
            TseLinks.officialPage(2026, 20322002026, 42, "SP", "SP"),
        )
        assertEquals(
            "https://divulgacandcontas.tse.jus.br/divulga/#/candidato/BR/BR/20322002026/42/2026/BR",
            TseLinks.officialPage(2026, 20322002026, 42, "BR", "BR", listOf("42" to "https://evil.example/x")),
        )
        assertFalse(TseLinks.isTseUrl("http://divulgacandcontas.tse.jus.br/divulga/"))
    }

    @Test
    fun lastUpdateAcceptsOnlyTheDocumentedFormat() {
        assertEquals(LocalDateTime.of(2026, 9, 18, 14, 39), parseLastUpdate("2026-09-18 14:39"))
        assertNull(parseLastUpdate("1723300401000"))
        assertNull(parseLastUpdate("18/09/2026"))
    }

    @Test
    fun detailEntityRoundTripsThroughRoom() {
        val dto = TseJson.decodeFromString(CandidatoDto.serializer(), Fixtures.text("tse/candidato-buscar-com-vice.json"))
        val detail = requireNotNull(dto.toCandidateDetailOrNull(TestElections.MUNICIPAL_2024, "81809", null))

        val entity: CandidateDetailEntity = detail.toEntity()

        assertEquals(detail, entity.toDomain(detail.runningMateEntities()))
    }

    @Test
    fun errorCodesRoundTrip() {
        val errors = listOf(AppError.Network, AppError.Blocked(403), AppError.Blocked(null), AppError.NotFound, AppError.Server(503), AppError.Parsing)

        assertEquals(errors, errors.map { AppErrorCodec.decode(AppErrorCodec.encode(it)) })
        assertEquals(AppError.Unknown(null), AppErrorCodec.decode(AppErrorCodec.encode(AppError.Unknown(IllegalStateException("x")))))
    }
}
