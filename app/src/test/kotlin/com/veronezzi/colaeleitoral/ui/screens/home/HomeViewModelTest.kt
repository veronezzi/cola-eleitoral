package com.veronezzi.colaeleitoral.ui.screens.home

import app.cash.turbine.test
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.OfficeRules
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.ui.common.ElectionSelection
import com.veronezzi.colaeleitoral.ui.common.LoadState
import com.veronezzi.colaeleitoral.ui.testing.FakeBallotRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeCandidateRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeElectionRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeReminderScheduler
import com.veronezzi.colaeleitoral.ui.testing.FakeSettingsRepository
import com.veronezzi.colaeleitoral.ui.testing.MainDispatcherRule
import com.veronezzi.colaeleitoral.ui.testing.UiTestData
import com.veronezzi.colaeleitoral.ui.testing.UiTestData.ELECTION_ID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val elections = FakeElectionRepository(listOf(UiTestData.election2026, UiTestData.municipal2024)).apply {
        offices.value = offices.value.copy(value = UiTestData.spOffices)
    }
    private val candidates = FakeCandidateRepository()
    private val ballot = FakeBallotRepository()
    private val reminders = FakeReminderScheduler()
    private val selection = ElectionSelection()
    private val settings = FakeSettingsRepository(UserSettings(location = UiTestData.sp, acceptedDisclaimerVersion = 1))

    private fun viewModel(clockAt: String = "2026-10-01T15:00:00Z") = HomeViewModel(
        settingsRepository = settings,
        electionRepository = elections,
        candidateRepository = candidates,
        ballotRepository = ballot,
        reminderScheduler = reminders,
        electionSelection = selection,
        clock = UiTestData.clockAt(clockAt),
    )

    @Test
    fun `shows the current election, the countdown and the ballot with picks`() = runTest {
        ballot.picks.value = listOf(UiTestData.pick(UiTestData.senators[1], slot = 1))
        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertEquals(LoadState.Loaded, state.loadState)
            assertEquals(ELECTION_ID, state.election?.id)
            assertEquals(Round.FIRST, state.election?.round)
            assertEquals(3L, state.election?.daysUntil)
            val slots = (state.ballot as BallotSection.Slots).slots
            assertEquals(6, slots.size)
            assertEquals("111", slots.first { it.key == "5:1" }.pick?.candidateNumber)
            assertTrue(state.showsPicks)
            assertTrue(state.offerReminder)
        }
    }

    @Test
    fun `pre-loads the candidate lists of the ballot for offline use`() = runTest {
        viewModel()
        assertEquals(
            UiTestData.spOffices.map { Triple(ELECTION_ID, it.ueCode, it.code) },
            candidates.refreshedLists,
        )
    }

    @Test
    fun `between the rounds, offices without runoff candidates disappear`() = runTest {
        val runoff = UiTestData.candidate(10, 13, "Gil Turno", "PGT", officeCode = OfficeRules.PRESIDENT, ueCode = "BR", totalization = "2º turno")
        candidates.setCandidates(ELECTION_ID, "BR", OfficeRules.PRESIDENT, listOf(runoff))
        candidates.setCandidates(
            ELECTION_ID,
            "SP",
            OfficeRules.GOVERNOR,
            listOf(UiTestData.candidate(11, 22, "Hugo Eleito", "PHE", officeCode = OfficeRules.GOVERNOR, totalization = "Eleito")),
        )
        viewModel(clockAt = "2026-10-10T15:00:00Z").uiState.test {
            val state = expectMostRecentItem()
            assertEquals(Round.SECOND, state.election?.round)
            val slots = (state.ballot as BallotSection.Slots).slots
            assertEquals(listOf(OfficeRules.PRESIDENT), slots.map { it.office.code })
        }
    }

    @Test
    fun `says there is no runoff here only when every office has a first-round winner`() = runTest {
        candidates.setCandidates(
            ELECTION_ID,
            "BR",
            OfficeRules.PRESIDENT,
            listOf(UiTestData.candidate(10, 13, "Ana Eleita", "PAE", officeCode = OfficeRules.PRESIDENT, ueCode = "BR", totalization = "Eleito")),
        )
        candidates.setCandidates(
            ELECTION_ID,
            "SP",
            OfficeRules.GOVERNOR,
            listOf(UiTestData.candidate(11, 22, "Hugo Eleito", "PHE", officeCode = OfficeRules.GOVERNOR, totalization = "ELEITO")),
        )
        viewModel(clockAt = "2026-10-10T15:00:00Z").uiState.test {
            assertEquals(BallotSection.NoRunoffHere, expectMostRecentItem().ballot)
        }
    }

    @Test
    fun `keeps offices whose first-round result the TSE has not published yet`() = runTest {
        // Real open data of 05/10/2026: governor already decided, every president still "#NULO".
        candidates.setCandidates(
            ELECTION_ID,
            "BR",
            OfficeRules.PRESIDENT,
            listOf(
                UiTestData.candidate(10, 13, "Gil Nulo", "PGN", officeCode = OfficeRules.PRESIDENT, ueCode = "BR", totalization = null),
                UiTestData.candidate(12, 45, "Rui Nulo", "PRN", officeCode = OfficeRules.PRESIDENT, ueCode = "BR", totalization = null),
            ),
        )
        candidates.setCandidates(
            ELECTION_ID,
            "SP",
            OfficeRules.GOVERNOR,
            listOf(
                UiTestData.candidate(11, 22, "Hugo Eleito", "PHE", officeCode = OfficeRules.GOVERNOR, totalization = "ELEITO"),
                UiTestData.candidate(14, 33, "Ivo Fora", "PIF", officeCode = OfficeRules.GOVERNOR, totalization = "NÃO ELEITO"),
            ),
        )
        viewModel(clockAt = "2026-10-10T15:00:00Z").uiState.test {
            val ballot = expectMostRecentItem().ballot as BallotSection.Slots
            assertEquals(listOf(OfficeRules.PRESIDENT), ballot.slots.map { it.office.code })
            assertEquals(listOf("Presidente"), ballot.pendingResultOffices)
        }
    }

    @Test
    fun `empty lists between the rounds are pending, not a missing runoff`() = runTest {
        candidates.setCandidates(ELECTION_ID, "BR", OfficeRules.PRESIDENT, emptyList())
        candidates.setCandidates(ELECTION_ID, "SP", OfficeRules.GOVERNOR, emptyList())
        viewModel(clockAt = "2026-10-10T15:00:00Z").uiState.test {
            val ballot = expectMostRecentItem().ballot as BallotSection.Slots
            assertEquals(setOf(OfficeRules.PRESIDENT, OfficeRules.GOVERNOR), ballot.slots.map { it.office.code }.toSet())
            assertEquals(2, ballot.pendingResultOffices.size)
        }
    }

    @Test
    fun `a municipal election without municipality asks for it`() = runTest {
        selection.select(UiTestData.municipal2024.id)
        viewModel().uiState.test {
            assertEquals(BallotSection.Unavailable(NoBallotReason.NEEDS_MUNICIPALITY), expectMostRecentItem().ballot)
        }
        settings.state.value = settings.state.value.copy(location = VoterLocation("DF"))
        viewModel().uiState.test {
            assertEquals(BallotSection.Unavailable(NoBallotReason.NO_MUNICIPAL_ELECTION_IN_DF), expectMostRecentItem().ballot)
        }
    }

    @Test
    fun `without cached elections the refresh error is shown`() = runTest {
        elections.elections.value = elections.elections.value.copy(value = emptyList(), fetchedAt = null)
        elections.refreshElectionsResult = AppResult.Failure(AppError.Network)
        val vm = viewModel()
        vm.onScreenResumed()
        vm.uiState.test {
            assertEquals(LoadState.Failed(AppError.Network), expectMostRecentItem().loadState)
        }
    }

    @Test
    fun `turning the reminder on schedules the upcoming first round`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            expectMostRecentItem()
            vm.onReminderEnabled()
            assertEquals(false, expectMostRecentItem().offerReminder)
        }
        assertEquals(listOf(Triple(ELECTION_ID, Round.FIRST, LocalDate.of(2026, 10, 4))), reminders.scheduled)
    }
}
