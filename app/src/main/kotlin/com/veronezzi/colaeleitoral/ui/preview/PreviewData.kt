package com.veronezzi.colaeleitoral.ui.preview

import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.domain.model.Candidate
import com.veronezzi.colaeleitoral.domain.model.CandidateDetail
import com.veronezzi.colaeleitoral.domain.model.CandidateStatus
import com.veronezzi.colaeleitoral.domain.model.DataSource
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectionScope
import com.veronezzi.colaeleitoral.domain.model.Office
import com.veronezzi.colaeleitoral.domain.model.OfficeRules
import com.veronezzi.colaeleitoral.domain.model.Party
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.RunningMate
import com.veronezzi.colaeleitoral.ui.common.Freshness
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Fictitious data for @Preview only: invented names and party acronyms, so no preview (or
 * screenshot taken from one) shows a real party or candidate.
 */
internal object PreviewData {
    val election = Election(
        id = 20322002026,
        year = 2026,
        name = "Eleição Geral Federal 2026",
        round = null,
        scope = ElectionScope.GENERAL,
        date = LocalDate.of(2026, 10, 4),
    )

    val offices: List<Office> = listOfNotNull(
        OfficeRules.office(OfficeRules.FEDERAL_DEPUTY, "Deputado Federal", "SP", 2026, Round.FIRST, 1132),
        OfficeRules.office(OfficeRules.STATE_DEPUTY, "Deputado Estadual", "SP", 2026, Round.FIRST, 1431),
        OfficeRules.office(OfficeRules.SENATOR, "Senador", "SP", 2026, Round.FIRST, 12),
        OfficeRules.office(OfficeRules.GOVERNOR, "Governador", "SP", 2026, Round.FIRST, 9),
        OfficeRules.office(OfficeRules.PRESIDENT, "Presidente", "BR", 2026, Round.FIRST, 11),
    )

    val senator: Office = offices.first { it.code == OfficeRules.SENATOR }

    private fun candidate(id: Long, number: Int, name: String, party: String, status: String = "Deferido") =
        Candidate(
            id = id,
            electionId = election.id,
            ueCode = "SP",
            officeCode = OfficeRules.SENATOR,
            number = number,
            ballotName = name,
            fullName = "$name da Silva Exemplo",
            party = Party(acronym = party, number = Party.numberFromCandidateNumber(number)),
            coalition = "Federação Exemplo ($party/PDX)",
            status = CandidateStatus(registration = status, totalization = "Concorrendo"),
            photoUrl = null,
        )

    val candidates: List<Candidate> = listOf(
        candidate(1, 111, "Ana Exemplo", "PEX"),
        candidate(2, 123, "Bruno Fictício", "PFI"),
        candidate(3, 222, "Carla Modelo", "PMO", status = "Aguardando julgamento"),
        candidate(4, 333, "Davi Amostra", "PAM", status = "Indeferido"),
        candidate(5, 456, "Elisa Teste", "PTE"),
    )

    val detail = CandidateDetail(
        candidate = candidates.first(),
        coalitionType = "Federação",
        coalitionComposition = "PEX / PDX",
        runningMates = listOf(
            RunningMate(10, "1º Suplente", "Fábio Suplente", null, "PEX", null, null),
            RunningMate(11, "2º Suplente", "Gina Suplente", null, "PDX", null, null),
        ),
        officialPageUrl = "https://divulgacandcontas.tse.jus.br/divulga/",
        photoPublishable = true,
        lastUpdate = LocalDateTime.of(2026, 9, 18, 14, 39),
    )

    fun pick(office: Office, candidate: Candidate, slot: Int = 1, statusAtSave: String = "Deferido") = BallotPick(
        electionId = election.id,
        electionYear = election.year,
        round = Round.FIRST,
        officeCode = office.code,
        officeName = office.name,
        urnaOrder = office.urnaOrder,
        digitCount = office.digitCount,
        slot = slot,
        ueCode = office.ueCode,
        candidateId = candidate.id,
        candidateNumber = candidate.number.toString().padStart(office.digitCount, '0'),
        ballotName = candidate.ballotName,
        partyAcronym = candidate.party.acronym,
        coalition = candidate.coalition,
        runningMateNames = emptyList(),
        statusAtSave = statusAtSave,
        savedAt = Instant.parse("2026-10-01T12:00:00Z"),
    )

    val picks: List<BallotPick> = listOf(
        pick(senator, candidates[0], slot = 1),
        pick(senator, candidates[1], slot = 2, statusAtSave = "Aguardando julgamento"),
    )

    fun freshness(minutesAgo: Long = 12, stale: Boolean = false) = Freshness(
        fetchedAt = Instant.now().minus(Duration.ofMinutes(minutesAgo)),
        isStale = stale,
        lastError = null,
        source = DataSource.DIVULGA_CAND_CONTAS,
    )
}
