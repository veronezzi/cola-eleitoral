package com.veronezzi.colaeleitoral.ui.common

import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.ui.testing.UiTestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class UiHelpersTest {
    @Test
    fun `only https links on tse jus br are opened from data`() {
        assertTrue(TseLinks.isTseUrl("https://divulgacandcontas.tse.jus.br/divulga/#/candidato/2026/1/SP/2"))
        assertTrue(TseLinks.isTseUrl("https://tse.jus.br/"))
        assertFalse(TseLinks.isTseUrl("http://divulgacandcontas.tse.jus.br/"))
        assertFalse(TseLinks.isTseUrl("https://tse.jus.br.example.com/"))
        assertFalse(TseLinks.isTseUrl("javascript:alert(1)"))
        assertEquals(TseLinks.DIVULGA_HOME, TseLinks.officialOrHome("https://example.com/"))
    }

    @Test
    fun `candidate page follows the general-election format and falls back for municipalities`() {
        assertEquals(
            "https://divulgacandcontas.tse.jus.br/divulga/#/candidato/BR/BR/20322002026/9/2026/BR",
            TseLinks.candidatePage(2026, 20322002026, "BR", 9),
        )
        assertEquals(TseLinks.DIVULGA_HOME, TseLinks.candidatePage(2024, 2045202024, "71072", 9))
    }

    @Test
    fun `numbers are spoken digit by digit and padded to the urna digits`() {
        assertEquals("1 2 3", spokenDigits("123"))
        assertEquals("0450", candidateNumberText(450, 4))
        assertEquals("13", candidateNumberText(13, null))
    }

    @Test
    fun `initials and relative age`() {
        assertEquals("MS", initialsOf("Maria da Silva"))
        assertEquals("J", initialsOf(" José "))
        val now = Instant.parse("2026-10-04T12:00:00Z")
        assertEquals(RelativeAge.JustNow, relativeAge(now.minusSeconds(30), now))
        assertEquals(RelativeAge.Minutes(5), relativeAge(now.minusSeconds(300), now))
        assertEquals(RelativeAge.Hours(2), relativeAge(now.minusSeconds(7_300), now))
        assertEquals(RelativeAge.Days(3), relativeAge(now.minusSeconds(3 * 86_400L + 10), now))
    }

    @Test
    fun `rounds close after their date and picks outside the ballot are kept apart`() {
        val election = UiTestData.election2026
        assertTrue(election.isRoundOpen(Round.FIRST, LocalDate.of(2026, 10, 4)))
        assertFalse(election.isRoundOpen(Round.FIRST, LocalDate.of(2026, 10, 5)))
        assertTrue(election.isRoundOpen(Round.SECOND, LocalDate.of(2026, 10, 25)))

        val slots = buildBallotSlots(UiTestData.spOffices, emptyList())
        val elsewhere = UiTestData.pick(UiTestData.senators[0], slot = 1).copy(officeCode = 99, slot = 1)
        assertEquals(listOf(elsewhere), picksOutsideBallot(slots, listOf(elsewhere)))
    }
}
