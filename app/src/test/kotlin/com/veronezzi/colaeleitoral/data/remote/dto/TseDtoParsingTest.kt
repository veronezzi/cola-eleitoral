package com.veronezzi.colaeleitoral.data.remote.dto

import com.veronezzi.colaeleitoral.core.network.TseJson
import com.veronezzi.colaeleitoral.data.testing.Fixtures
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DTOs against real DivulgaCandContas responses (app/src/test/resources/tse). */
class TseDtoParsingTest {
    @Test
    fun ordinaryElectionsKeepLongIdsAndNullDates() {
        val array = TseJson.parseToJsonElement(Fixtures.text("tse/eleicoes-ordinarias.json")).jsonArray
        val elections = TseJson.decodeFromJsonElement(EleicaoListSerializer, array)

        assertEquals(13, elections.size)
        val current = elections.first()
        assertEquals(20322002026L, current.id)
        assertEquals(2026, current.ano)
        assertEquals("F", current.tipoAbrangencia)
        assertEquals("2026-10-04", current.dataEleicao)
        assertNull(current.turno?.contentOrNull)
        assertNull(elections.single { it.id == 14417L }.dataEleicao)
    }

    @Test
    fun municipalityCodesStayTextWithLeadingZeros() {
        val dto = TseJson.decodeFromString(MunicipiosResponseDto.serializer(), Fixtures.text("tse/municipios.json"))

        assertEquals("AC", dto.estado?.sigla)
        assertTrue(dto.municipios.any { it.codigo == "01120" && it.nome == "ACRELÂNDIA" })
        assertTrue(dto.municipios.all { it.sigla == null })
    }

    @Test
    fun candidateListParses() {
        val dto = TseJson.decodeFromString(CandidatosResponseDto.serializer(), Fixtures.text("tse/candidatos-listar.json"))

        assertEquals("BR", dto.unidadeEleitoral?.codigo)
        assertEquals(1, dto.cargo?.codigo)
        assertEquals(13, dto.candidatos.size)
        val lula = dto.candidatos.single { it.id == 280001607829L }
        assertEquals(13, lula.numero)
        assertEquals(0, lula.partido?.numero)
        assertEquals("COLIGAÇÃO BRASIL DA ESPERANÇA", lula.nomeColigacao)
        assertNull(lula.fotoUrl)
        assertTrue(lula.vices.isEmpty())
    }

    @Test
    fun candidateDetailOf2026Parses() {
        val dto = TseJson.decodeFromString(CandidatoDto.serializer(), Fixtures.text("tse/candidato-buscar.json"))

        assertEquals(250002539612L, dto.id)
        assertEquals("2026-09-18 14:39", dto.dataUltimaAtualizacao?.contentOrNull)
        assertEquals("Consta da urna", dto.descricaoSituacaoCandidato)
        assertEquals(true, dto.fotoUrlPublicavel)
        assertEquals(50, dto.partido?.numero)
        assertTrue(dto.vices.isEmpty())
        assertEquals(4, dto.eleicoesAnteriores.size)
        assertEquals(20322002026L, dto.eleicao?.id)
    }

    @Test
    fun runningMatesUseTheUpperSnakeKeys() {
        val dto = TseJson.decodeFromString(CandidatoDto.serializer(), Fixtures.text("tse/candidato-buscar-com-vice.json"))

        val vice = dto.vices.single()
        assertEquals(240001918431L, vice.sqCandidato)
        assertEquals("SILVESTRE", vice.nmUrna)
        assertEquals("Vice-prefeito", vice.dsCargo)
        assertEquals("NOVO", vice.sgPartido)
        assertEquals(true, vice.urlFotoPublicavel)
    }

    @Test
    fun oneMalformedItemDoesNotDropTheList() {
        val json = """{"candidatos":[{"id":1,"numero":"abc"},{"id":2,"numero":13,"nomeUrna":"X","partido":{"sigla":"Y"}}]}"""

        val dto = TseJson.decodeFromString(CandidatosResponseDto.serializer(), json)

        assertEquals(listOf(2L), dto.candidatos.map { it.id })
    }

    /** The API sends CPF, título, birth date, e-mails...: no DTO may declare them (LGPD). */
    @Test
    fun dtosDeclareNoPersonalDataFields() {
        val forbidden = setOf(
            "cpf", "tituloEleitor", "dataDeNascimento", "emails", "descricaoSexo", "descricaoCorRaca",
            "descricaoEstadoCivil", "grauInstrucao", "ocupacao", "infoComplementar", "legenda", "bens",
            "totalDeBens", "arquivos", "sites", "cnpjcampanha", "nacionalidade", "sgUfNascimento",
            "nomeMunicipioNascimento", "descricaoNaturalidade", "motivos",
        )
        val names = listOf(
            EleicaoDto.serializer().descriptor,
            MunicipiosResponseDto.serializer().descriptor,
            CargosResponseDto.serializer().descriptor,
            CandidatosResponseDto.serializer().descriptor,
            CandidatoDto.serializer().descriptor,
        ).flatMap { serialNames(it) }.toSet()

        assertTrue("PII declared: ${names intersect forbidden}", (names intersect forbidden).isEmpty())
        assertTrue(names.none { it.startsWith("gastoCampanha") || it.startsWith("numeroProcesso") || it.startsWith("st_MOTIVO") })
    }

    private fun serialNames(descriptor: SerialDescriptor, seen: MutableSet<String> = mutableSetOf()): Set<String> {
        if (!seen.add(descriptor.serialName)) return emptySet()
        val names = mutableSetOf<String>()
        for (index in 0 until descriptor.elementsCount) {
            names += descriptor.getElementName(index)
            names += serialNames(descriptor.getElementDescriptor(index), seen)
        }
        return names
    }
}
