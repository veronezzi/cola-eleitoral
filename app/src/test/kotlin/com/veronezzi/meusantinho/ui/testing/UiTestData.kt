package com.veronezzi.meusantinho.ui.testing

import com.veronezzi.meusantinho.domain.model.BallotPick
import com.veronezzi.meusantinho.domain.model.Candidate
import com.veronezzi.meusantinho.domain.model.CandidateStatus
import com.veronezzi.meusantinho.domain.model.Election
import com.veronezzi.meusantinho.domain.model.ElectionScope
import com.veronezzi.meusantinho.domain.model.Office
import com.veronezzi.meusantinho.domain.model.OfficeRules
import com.veronezzi.meusantinho.domain.model.Party
import com.veronezzi.meusantinho.domain.model.Round
import com.veronezzi.meusantinho.domain.model.VoterLocation
import com.veronezzi.meusantinho.ui.common.AppClock
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Sets [Dispatchers.Main] to a test dispatcher whose scheduler `runTest` shares. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Fictitious data (invented names and parties). */
object UiTestData {
    const val ELECTION_ID = 20322002026L

    val election2026 = Election(
        id = ELECTION_ID,
        year = 2026,
        name = "Eleição Geral Federal 2026",
        round = null,
        scope = ElectionScope.GENERAL,
        date = LocalDate.of(2026, 10, 4),
    )

    val municipal2024 = Election(
        id = 2045202024L,
        year = 2024,
        name = "Eleições Municipais 2024",
        round = null,
        scope = ElectionScope.MUNICIPAL,
        date = LocalDate.of(2024, 10, 6),
    )

    val sp = VoterLocation(uf = "SP")

    /** 2026-10-01 12:00 in Brasília: three days before the first round. */
    fun clockAt(instant: String = "2026-10-01T15:00:00Z"): AppClock = AppClock(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC))

    /** The general-election ballot of SP, first-round shape (two Senate votes in 2026). */
    val spOffices: List<Office> = OfficeRules.defaultCodes(ElectionScope.GENERAL, "SP").map { code ->
        OfficeRules.office(
            code = code,
            name = OfficeRules.canonicalName(code)!!,
            ueCode = OfficeRules.ueCodeFor(code, sp, ElectionScope.GENERAL)!!,
            year = 2026,
            round = Round.FIRST,
        )!!
    }

    fun candidate(
        id: Long,
        number: Int,
        name: String,
        party: String,
        status: String = "Deferido",
        officeCode: Int = OfficeRules.SENATOR,
        ueCode: String = "SP",
        totalization: String? = "Concorrendo",
        isFit: Boolean? = null,
    ) = Candidate(
        id = id,
        electionId = ELECTION_ID,
        ueCode = ueCode,
        officeCode = officeCode,
        number = number,
        ballotName = name,
        fullName = "$name Exemplo",
        party = Party(party, Party.numberFromCandidateNumber(number)),
        coalition = null,
        status = CandidateStatus(registration = status, totalization = totalization, isFit = isFit),
        photoUrl = null,
    )

    val senators = listOf(
        candidate(1, 456, "Elisa Teste", "PTE"),
        candidate(2, 111, "Ana Exemplo", "PEX"),
        candidate(3, 123, "Bruno Fictício", "PFI", status = "Indeferido", isFit = false),
        candidate(4, 222, "Carla Modelo", "PMO", status = "Aguardando julgamento"),
    )

    fun pick(
        candidate: Candidate,
        slot: Int = 1,
        office: Office = spOffices.first { it.code == candidate.officeCode },
        round: Round = Round.FIRST,
        statusAtSave: String = candidate.status.registration,
        ueCode: String = office.ueCode,
    ) = BallotPick(
        electionId = ELECTION_ID,
        electionYear = 2026,
        round = round,
        officeCode = office.code,
        officeName = office.name,
        urnaOrder = office.urnaOrder,
        digitCount = office.digitCount,
        slot = slot,
        ueCode = ueCode,
        candidateId = candidate.id,
        candidateNumber = candidate.number.toString().padStart(office.digitCount, '0'),
        ballotName = candidate.ballotName,
        partyAcronym = candidate.party.acronym,
        coalition = candidate.coalition,
        runningMateNames = emptyList(),
        statusAtSave = statusAtSave,
        savedAt = Instant.parse("2026-09-30T12:00:00Z"),
    )
}
