package com.veronezzi.colaeleitoral.ui.screens.candidates

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.OfficeRules
import com.veronezzi.colaeleitoral.domain.model.SortOrder
import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.ui.common.UiDispatchers
import com.veronezzi.colaeleitoral.ui.navigation.CandidateListRoute
import com.veronezzi.colaeleitoral.ui.testing.FakeBallotRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeCandidateRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeElectionRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeSettingsRepository
import com.veronezzi.colaeleitoral.ui.testing.MainDispatcherRule
import com.veronezzi.colaeleitoral.ui.testing.UiTestData
import com.veronezzi.colaeleitoral.ui.testing.UiTestData.ELECTION_ID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CandidateListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val candidates = FakeCandidateRepository()
    private val elections = FakeElectionRepository(listOf(UiTestData.election2026)).apply {
        offices.value = offices.value.copy(value = UiTestData.spOffices)
    }
    private val ballot = FakeBallotRepository()
    private val settings = FakeSettingsRepository(UserSettings(location = UiTestData.sp, acceptedDisclaimerVersion = 1))

    private fun viewModel(round: Int = 1, slot: Int = 1) = CandidateListViewModel(
        route = CandidateListRoute(ELECTION_ID, 2026, "SP", OfficeRules.SENATOR, round, slot),
        savedStateHandle = SavedStateHandle(),
        candidateRepository = candidates,
        electionRepository = elections,
        settingsRepository = settings,
        ballotRepository = ballot,
        dispatchers = UiDispatchers(mainDispatcherRule.dispatcher),
    )

    @Test
    fun `lists every candidate by number by default, never ranked`() = runTest {
        candidates.setCandidates(ELECTION_ID, "SP", OfficeRules.SENATOR, UiTestData.senators)
        val vm = viewModel()
        vm.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(ListContent.Items, state.content)
            assertEquals(listOf(111, 123, 222, 456), state.items.map { it.candidate.number })
            assertEquals(4, state.totalCount)
            assertEquals(SortOrder.NUMBER, state.filter.sortOrder)
            assertEquals("Senador", state.officeName)
            assertEquals(2, state.maxPicks)
            assertEquals("São Paulo", state.unitName)
            assertTrue(state.showPhotos)
        }
    }

    @Test
    fun `search waits for the debounce and matches name, number or party`() = runTest {
        candidates.setCandidates(ELECTION_ID, "SP", OfficeRules.SENATOR, UiTestData.senators)
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(4, expectMostRecentItem().items.size)

            vm.onQueryChange("bruno")
            advanceTimeBy(CandidateListViewModel.SEARCH_DEBOUNCE_MILLIS - 50)
            assertEquals("bruno", expectMostRecentItem().query)
            assertEquals(4, vm.uiState.value.items.size)

            advanceTimeBy(100)
            assertEquals(listOf("Bruno Fictício"), expectMostRecentItem().items.map { it.candidate.ballotName })

            vm.onQueryChange("12")
            advanceTimeBy(300)
            assertEquals(listOf(123), expectMostRecentItem().items.map { it.candidate.number })

            vm.onQueryChange("pmo")
            advanceTimeBy(300)
            assertEquals(listOf("PMO"), expectMostRecentItem().items.map { it.candidate.party.acronym })
        }
    }

    @Test
    fun `party and status filters combine, and clearing them shows everyone again`() = runTest {
        candidates.setCandidates(ELECTION_ID, "SP", OfficeRules.SENATOR, UiTestData.senators)
        val vm = viewModel()
        vm.uiState.test {
            vm.onPartyToggled("PEX")
            vm.onPartyToggled("PFI")
            assertEquals(listOf(111, 123), expectMostRecentItem().items.map { it.candidate.number })

            vm.onStatusToggled("Indeferido")
            val filtered = expectMostRecentItem()
            assertEquals(listOf(123), filtered.items.map { it.candidate.number })
            assertEquals(3, filtered.activeFilterCount)
            assertEquals(listOf("PEX", "PFI", "PMO", "PTE"), filtered.options.parties.map { it.acronym })

            vm.onClearFilters()
            assertEquals(4, expectMostRecentItem().items.size)
        }
    }

    @Test
    fun `sorts alphabetically by name on request`() = runTest {
        candidates.setCandidates(ELECTION_ID, "SP", OfficeRules.SENATOR, UiTestData.senators)
        val vm = viewModel()
        vm.uiState.test {
            vm.onSortChange(SortOrder.BALLOT_NAME)
            assertEquals(
                listOf("Ana Exemplo", "Bruno Fictício", "Carla Modelo", "Elisa Teste"),
                expectMostRecentItem().items.map { it.candidate.ballotName },
            )
        }
    }

    @Test
    fun `marks the pick of this vote and the other Senate vote`() = runTest {
        candidates.setCandidates(ELECTION_ID, "SP", OfficeRules.SENATOR, UiTestData.senators)
        ballot.picks.value = listOf(
            UiTestData.pick(UiTestData.senators[1], slot = 1),
            UiTestData.pick(UiTestData.senators[2], slot = 2),
        )
        val vm = viewModel(slot = 1)
        vm.uiState.test {
            val items = expectMostRecentItem().items.associateBy { it.candidate.id }
            assertTrue(items.getValue(2).isSaved)
            assertTrue(items.getValue(3).isInOtherSlot)
            assertEquals(false, items.getValue(1).isSaved || items.getValue(1).isInOtherSlot)
        }
    }

    @Test
    fun `distinguishes an empty TSE list from an empty search`() = runTest {
        candidates.setCandidates(ELECTION_ID, "SP", OfficeRules.SENATOR, emptyList())
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(ListContent.EmptyFromTse, expectMostRecentItem().content)
            candidates.setCandidates(ELECTION_ID, "SP", OfficeRules.SENATOR, UiTestData.senators)
            vm.onQueryChange("ninguém com este nome")
            advanceTimeBy(300)
            assertEquals(ListContent.EmptyForFilters, expectMostRecentItem().content)
        }
    }

    @Test
    fun `shows the error type when nothing is cached`() = runTest {
        candidates.refreshCandidatesResult = AppResult.Failure(AppError.Blocked(403))
        val vm = viewModel()
        vm.uiState.test {
            assertEquals(ListContent.Failed(AppError.Blocked(403)), expectMostRecentItem().content)
        }
    }

    @Test
    fun `second round starts with only runoff candidates`() = runTest {
        val runoff = UiTestData.candidate(10, 13, "Gil Turno", "PGT", officeCode = OfficeRules.GOVERNOR, totalization = "2º turno")
        val out = UiTestData.candidate(11, 22, "Hugo Fora", "PHF", officeCode = OfficeRules.GOVERNOR, totalization = "Não eleito")
        candidates.setCandidates(ELECTION_ID, "SP", OfficeRules.GOVERNOR, listOf(runoff, out))
        val vm = CandidateListViewModel(
            route = CandidateListRoute(ELECTION_ID, 2026, "SP", OfficeRules.GOVERNOR, round = 2),
            savedStateHandle = SavedStateHandle(),
            candidateRepository = candidates,
            electionRepository = elections,
            settingsRepository = settings,
            ballotRepository = ballot,
            dispatchers = UiDispatchers(mainDispatcherRule.dispatcher),
        )
        vm.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.filter.onlySecondRound)
            assertEquals(listOf(10L), state.items.map { it.candidate.id })
            vm.onOnlySecondRoundChange(false)
            assertEquals(2, expectMostRecentItem().items.size)
        }
    }
}
