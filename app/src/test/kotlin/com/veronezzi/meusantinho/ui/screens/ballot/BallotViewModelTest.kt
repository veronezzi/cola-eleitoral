package com.veronezzi.meusantinho.ui.screens.ballot

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.veronezzi.meusantinho.domain.model.CachedData
import com.veronezzi.meusantinho.domain.model.OfficeRules
import com.veronezzi.meusantinho.domain.model.UserSettings
import com.veronezzi.meusantinho.ui.navigation.BallotRoute
import com.veronezzi.meusantinho.ui.testing.FakeBallotRepository
import com.veronezzi.meusantinho.ui.testing.FakeCandidateRepository
import com.veronezzi.meusantinho.ui.testing.FakeElectionRepository
import com.veronezzi.meusantinho.ui.testing.FakeSettingsRepository
import com.veronezzi.meusantinho.ui.testing.MainDispatcherRule
import com.veronezzi.meusantinho.ui.testing.UiTestData
import com.veronezzi.meusantinho.ui.testing.UiTestData.ELECTION_ID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BallotViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val candidates = FakeCandidateRepository()
    private val elections = FakeElectionRepository(listOf(UiTestData.election2026)).apply {
        offices.value = offices.value.copy(value = UiTestData.spOffices)
    }
    private val ballot = FakeBallotRepository()
    private val settings = FakeSettingsRepository(UserSettings(location = UiTestData.sp, acceptedDisclaimerVersion = 1))

    private fun viewModel() = BallotViewModel(
        route = BallotRoute(ELECTION_ID, round = 1),
        savedStateHandle = SavedStateHandle(),
        electionRepository = elections,
        ballotRepository = ballot,
        candidateRepository = candidates,
        settingsRepository = settings,
        clock = UiTestData.clockAt(),
    )

    @Test
    fun `votes follow the urna order with two Senate votes in 2026`() = runTest {
        ballot.picks.value = listOf(UiTestData.pick(UiTestData.senators[1], slot = 2))
        viewModel().uiState.test {
            val state = expectMostRecentItem()
            val labels = state.entries.map { it.slot.voteNumber to "${it.slot.office.code}:${it.slot.slot}" }
            assertEquals(
                listOf(1 to "6:1", 2 to "7:1", 3 to "5:1", 4 to "5:2", 5 to "3:1", 6 to "1:1"),
                labels,
            )
            assertEquals("111", state.entries.first { it.slot.key == "5:2" }.slot.pick?.candidateNumber)
            assertTrue(state.hasPicks)
            assertTrue(state.canEdit)
        }
    }

    @Test
    fun `warns, verbatim, when the TSE status changed after saving`() = runTest {
        val saved = UiTestData.senators[3]
        ballot.picks.value = listOf(UiTestData.pick(saved, slot = 1, statusAtSave = "Aguardando julgamento"))
        candidates.setCandidates(
            ELECTION_ID,
            "SP",
            OfficeRules.SENATOR,
            listOf(saved.copy(status = saved.status.copy(registration = "Deferido com recurso"))),
        )
        viewModel().uiState.test {
            val entry = expectMostRecentItem().entries.first { it.slot.key == "5:1" }
            assertEquals("Deferido com recurso", entry.updatedStatus)
        }
    }

    @Test
    fun `renders from the snapshots alone when the public cache is gone`() = runTest {
        elections.elections.value = CachedData(emptyList(), null, isStale = true)
        ballot.picks.value = listOf(
            UiTestData.pick(UiTestData.senators[1], slot = 1),
            UiTestData.pick(UiTestData.senators[2], slot = 2),
        )
        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertEquals(listOf("5:1", "5:2"), state.entries.map { it.slot.key })
            assertNull(state.entries.firstOrNull { it.slot.pick == null })
        }
    }

    @Test
    fun `clearing asks for confirmation first`() = runTest {
        ballot.picks.value = listOf(UiTestData.pick(UiTestData.senators[1], slot = 1))
        val vm = viewModel()
        vm.uiState.test {
            vm.onClearRequested()
            assertTrue(expectMostRecentItem().confirmClear)
            assertEquals(1, ballot.picks.value.size)
            vm.onClearConfirmed()
            val cleared = expectMostRecentItem()
            assertFalse(cleared.confirmClear)
            assertFalse(cleared.hasPicks)
            assertEquals(BallotMessage.Cleared, cleared.message)
        }
    }

    @Test
    fun `refreshes the lists of picked offices to catch status changes`() = runTest {
        ballot.picks.value = listOf(UiTestData.pick(UiTestData.senators[1], slot = 1))
        viewModel()
        assertEquals(listOf(Triple(ELECTION_ID, "SP", OfficeRules.SENATOR)), candidates.refreshedLists)
    }
}
