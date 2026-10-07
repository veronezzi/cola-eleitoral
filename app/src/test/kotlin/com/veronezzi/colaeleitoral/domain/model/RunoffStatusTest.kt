package com.veronezzi.colaeleitoral.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Texts as the TSE publishes them. The open data of 05/10/2026 had the governor of AL decided
 * ("ELEITO" plus three "NÃO ELEITO") while all 14 presidential candidates were still "#NULO"
 * (parsed as no totalization): the app must not read that as "no runoff".
 */
class RunoffStatusTest {
    private fun candidates(vararg totalizations: String?) = totalizations.mapIndexed { index, text ->
        Candidate(
            id = index.toLong(),
            electionId = 1L,
            ueCode = "AL",
            officeCode = OfficeRules.GOVERNOR,
            number = 10 + index,
            ballotName = "Candidatura $index",
            fullName = null,
            party = Party(acronym = "PEX", name = "Partido Exemplo", number = 10 + index),
            coalition = null,
            status = CandidateStatus(registration = "DEFERIDO", totalization = text),
            photoUrl = null,
        )
    }

    @Test
    fun `a first-round winner means the office was decided`() {
        assertEquals(RunoffStatus.DECIDED, runoffStatusOf(candidates("ELEITO", "NÃO ELEITO", "NÃO ELEITO", "NÃO ELEITO")))
        assertEquals(RunoffStatus.DECIDED, runoffStatusOf(candidates("Eleito", "Não eleito")))
    }

    @Test
    fun `a candidate marked for the second round means a runoff`() {
        assertEquals(RunoffStatus.RUNOFF, runoffStatusOf(candidates("2º TURNO", "2º TURNO", "NÃO ELEITO")))
        assertEquals(RunoffStatus.RUNOFF, runoffStatusOf(candidates("2º turno", "Não eleito")))
    }

    @Test
    fun `an unpublished result is pending, not decided`() {
        assertEquals(RunoffStatus.PENDING, runoffStatusOf(candidates(null, null, null)))
        assertEquals(RunoffStatus.PENDING, runoffStatusOf(candidates("Concorrendo", "Concorrendo")))
        assertEquals(RunoffStatus.PENDING, runoffStatusOf(candidates("NÃO ELEITO", "NÃO ELEITO")))
        assertEquals(RunoffStatus.PENDING, runoffStatusOf(emptyList()))
    }

    @Test
    fun `elected texts include the proportional variants but never not elected`() {
        assertTrue(CandidateStatus.isElectedText("ELEITO POR QP"))
        assertTrue(CandidateStatus.isElectedText("Eleito por média"))
        assertTrue(CandidateStatus.isElectedText(" Eleita "))
        assertFalse(CandidateStatus.isElectedText("NÃO ELEITO"))
        assertFalse(CandidateStatus.isElectedText("Suplente"))
        assertFalse(CandidateStatus.isElectedText("2º TURNO"))
    }
}
