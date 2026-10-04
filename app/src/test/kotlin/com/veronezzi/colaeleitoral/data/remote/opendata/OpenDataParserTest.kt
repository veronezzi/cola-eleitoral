package com.veronezzi.colaeleitoral.data.remote.opendata

import com.veronezzi.colaeleitoral.data.testing.Fixtures
import com.veronezzi.colaeleitoral.data.testing.TestElections
import com.veronezzi.colaeleitoral.domain.model.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

/** Parser and mapper against the real `consulta_cand_2026` excerpts (tse-opendata/README.md). */
class OpenDataParserTest {
    private fun unit(ue: String): Map<Int, List<Candidate>> {
        val rows = Fixtures.bytes("tse-opendata/consulta_cand_2026_$ue.csv").inputStream().use(OpenDataParser::readCandidates)
        val statuses = Fixtures.bytes("tse-opendata/consulta_cand_complementar_2026_$ue.csv").inputStream()
            .use { OpenDataParser.readStatuses(it, rows.mapTo(HashSet()) { row -> row.candidateId }) }
        return OpenDataMapper.candidatesByOffice(TestElections.GENERAL_2026, ue, rows, statuses)
    }

    @Test
    fun realFilesParseEveryRow() {
        val rows = Fixtures.bytes("tse-opendata/consulta_cand_2026_AL.csv").inputStream().use(OpenDataParser::readCandidates)
        val statuses = Fixtures.bytes("tse-opendata/consulta_cand_complementar_2026_AL.csv").inputStream()
            .use { OpenDataParser.readStatuses(it, rows.mapTo(HashSet()) { row -> row.candidateId }) }

        assertEquals(26, rows.size)
        assertEquals(26, statuses.size)
        val renanFilho = rows.single { it.candidateId == 20002553745L }
        assertEquals("RENAN FILHO", renanFilho.ballotName)
        assertEquals("AL", renanFilho.ueCode)
        assertEquals("MDB", renanFilho.partyAcronym)
        assertEquals(15, renanFilho.partyNumber)
        assertEquals("COLIGAÇÃO", renanFilho.grouping)
        assertNull("#NE is no value", renanFilho.candidacyStatus)
        assertNull("#NULO is no value", renanFilho.totalization)
    }

    @Test
    fun onlyBallotOfficesBecomeCandidates() {
        val al = unit("AL")

        assertEquals(setOf(3, 5, 6, 7), al.keys)
        assertEquals(listOf(15, 80), al.getValue(3).map { it.number }.sorted())
        assertEquals(listOf(151, 156, 456), al.getValue(5).map { it.number }.sorted())
        assertEquals(listOf(1313, 2500, 2500, 2727, 7001), al.getValue(6).map { it.number }.sorted())
        assertEquals(listOf(13000, 25125, 27444), al.getValue(7).map { it.number }.sorted())
        assertEquals(listOf(13, 22, 28, 28), unit("BR").getValue(1).map { it.number }.sorted())
    }

    @Test
    fun statusComesVerbatimFromTheComplementaryFile() {
        val deputies = unit("AL").getValue(6)

        assertEquals(setOf("INDEFERIDO", "DEFERIDO"), deputies.filter { it.number == 2500 }.map { it.status.registration }.toSet())
        assertEquals("RENÚNCIA", deputies.single { it.number == 7001 }.status.registration)
        assertEquals("DEFERIDO EM PRAZO RECURSAL OU COM RECURSO", deputies.single { it.number == 2727 }.status.registration)
        val presidents = unit("BR").getValue(1)
        assertEquals(setOf("RENÚNCIA", "INDEFERIDO"), presidents.filter { it.number == 28 }.map { it.status.registration }.toSet())
        assertTrue(presidents.all { it.status.onBallot == null && it.status.isFit == null && it.status.totalization == null })
    }

    @Test
    fun candidatesCarryTheApiIdsAndNoPhotos() {
        val lula = unit("BR").getValue(1).single { it.number == 13 }

        assertEquals(280002542548L, lula.id)
        assertEquals(TestElections.GENERAL_2026.id, lula.electionId)
        assertEquals("BR", lula.ueCode)
        assertEquals("PT", lula.party.acronym)
        assertEquals(13, lula.party.number)
        assertNull(lula.photoUrl)
    }

    @Test
    fun coalitionShowsTheCoalitionOrFederationName() {
        val al = unit("AL")

        assertEquals("PRA ALAGOAS FAZER HISTÓRIA DE NOVO", al.getValue(3).single { it.number == 15 }.coalition)
        assertNull("isolated party", al.getValue(3).single { it.number == 80 }.coalition)
        assertEquals("ALAGOAS GIGANTE II", al.getValue(5).single { it.number == 456 }.coalition)
        assertEquals("FEDERAÇÃO BRASIL DA ESPERANÇA - FE BRASIL", al.getValue(6).single { it.number == 1313 }.coalition)
        assertEquals("BRASIL PRONTO PRA MAIS", unit("BR").getValue(1).single { it.number == 13 }.coalition)
    }

    @Test
    fun personalDataColumnsAreNeverMaterialized() {
        val csv = SYNTHETIC_HEADER + "\r\n" +
            "1;3;\"AL\";15;\"FULANA\";\"NOME CIVIL\";\"NOME SOCIAL\";\"CPF-MARKER\";\"EMAIL-MARKER\";\"MDB\";\"TITULO-MARKER\";\"NASC-MARKER\";1;\"2º TURNO\"\r\n"
        val header = SemicolonCsvReader(StringReader(csv)).let { reader ->
            reader.readHeader()!!.also {
                val seen = mutableListOf<String>()
                val kept = setOf(0, 4)
                reader.readRecord({ it in kept }) { _, value -> seen += value }
                assertEquals(listOf("1", "FULANA"), seen)
            }
        }
        assertEquals("NR_CPF_CANDIDATO", header[7])

        val rows = OpenDataParser.readCandidates(csv.byteInputStream(OpenDataParser.CHARSET))

        assertEquals(1, rows.size)
        assertFalse(rows.single().toString().contains("MARKER"))
    }

    @Test
    fun socialNameAndLatestRoundWin() {
        val csv = SYNTHETIC_HEADER + "\r\n" +
            "1;3;\"AL\";15;\"FULANA\";\"NOME CIVIL\";\"NOME SOCIAL\";\"\";\"\";\"MDB\";\"\";\"\";1;\"2º TURNO\"\r\n" +
            "1;3;\"AL\";15;\"FULANA\";\"NOME CIVIL\";\"NOME SOCIAL\";\"\";\"\";\"MDB\";\"\";\"\";2;\"#NULO\"\r\n"
        val rows = OpenDataParser.readCandidates(csv.byteInputStream(OpenDataParser.CHARSET))

        val candidate = OpenDataMapper.candidatesByOffice(TestElections.GENERAL_2026, "AL", rows, emptyMap()).getValue(3).single()

        assertEquals("NOME SOCIAL", candidate.fullName)
        assertEquals("2º TURNO", candidate.status.totalization)
        assertTrue(candidate.status.isInSecondRound)
        assertEquals("", candidate.status.registration)
    }

    @Test
    fun readerHandlesQuotesSeparatorsLineEndsAndBom() {
        val reader = SemicolonCsvReader(StringReader("﻿\"A\";\"B\"\n\"x;y\";\"say \"\"hi\"\"\"\n;\r\nlast;"))
        val records = mutableListOf<List<String>>()

        assertEquals(listOf("A", "B"), reader.readHeader())
        while (true) {
            val record = mutableListOf<String>()
            if (!reader.readRecord({ true }) { _, value -> record += value }) break
            records += record
        }

        assertEquals(listOf(listOf("x;y", "say \"hi\""), listOf("", ""), listOf("last", "")), records)
    }

    @Test
    fun missingRequiredColumnsFailAsFormatChange() {
        assertThrows(OpenDataFormatException::class.java) {
            OpenDataParser.readCandidates("\"SQ_CANDIDATO\";\"NR_CANDIDATO\"\r\n1;2\r\n".byteInputStream())
        }
    }

    private companion object {
        const val SYNTHETIC_HEADER = "\"SQ_CANDIDATO\";\"CD_CARGO\";\"SG_UE\";\"NR_CANDIDATO\";\"NM_URNA_CANDIDATO\";" +
            "\"NM_CANDIDATO\";\"NM_SOCIAL_CANDIDATO\";\"NR_CPF_CANDIDATO\";\"DS_EMAIL\";\"SG_PARTIDO\";" +
            "\"NR_TITULO_ELEITORAL_CANDIDATO\";\"DT_NASCIMENTO\";\"NR_TURNO\";\"DS_SIT_TOT_TURNO\""
    }
}
