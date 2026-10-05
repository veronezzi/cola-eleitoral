package com.veronezzi.colaeleitoral.ui.screens.detail

import app.cash.turbine.test
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.CachedData
import com.veronezzi.colaeleitoral.domain.model.CandidateDetail
import com.veronezzi.colaeleitoral.domain.model.DataSource
import com.veronezzi.colaeleitoral.domain.model.OfficeRules
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.ui.navigation.CandidateDetailRoute
import com.veronezzi.colaeleitoral.ui.testing.FakeBallotRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeCandidateRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeElectionRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeSettingsRepository
import com.veronezzi.colaeleitoral.ui.testing.MainDispatcherRule
import com.veronezzi.colaeleitoral.ui.testing.UiTestData
import com.veronezzi.colaeleitoral.ui.testing.UiTestData.ELECTION_ID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

class CandidateDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val candidates = FakeCandidateRepository().apply {
        setCandidates(ELECTION_ID, "SP", OfficeRules.SENATOR, UiTestData.senators)
    }
    private val elections = FakeElectionRepository(listOf(UiTestData.election2026)).apply {
        offices.value = offices.value.copy(value = UiTestData.spOffices)
    }
    private val ballot = FakeBallotRepository()
    private val settings = FakeSettingsRepository(UserSettings(location = UiTestData.sp, acceptedDisclaimerVersion = 1))

    private fun viewModel(candidateId: Long, slot: Int = 1, clockAt: String = "2026-10-01T15:00:00Z") =
        CandidateDetailViewModel(
            route = CandidateDetailRoute(ELECTION_ID, 2026, "SP", OfficeRules.SENATOR, candidateId, round = 1, slot = slot),
            candidateRepository = candidates,
            electionRepository = elections,
            ballotRepository = ballot,
            settingsRepository = settings,
            clock = UiTestData.clockAt(clockAt),
        )

    @Test
    fun `saving stores a snapshot in the vote of the route`() = runTest {
        val vm = viewModel(candidateId = 2, slot = 2)
        vm.uiState.test {
            val before = expectMostRecentItem()
            assertEquals(DetailContent.Loaded, before.content)
            assertEquals("111", before.numberText)
            assertEquals(PickState.NotSaved, before.pickState)

            vm.onSaveClick()
            val after = expectMostRecentItem()
            assertEquals(PickState.SavedHere, after.pickState)
            assertEquals(DetailMessage.Saved(statusNotice = null), after.message)
        }
        val saved = ballot.picks.value.single()
        assertEquals(2, saved.slot)
        assertEquals(Round.FIRST, saved.round)
        assertEquals("111", saved.candidateNumber)
        assertEquals(3, saved.digitCount)
        assertEquals(3, saved.urnaOrder)
        assertEquals("Deferido", saved.statusAtSave)
        assertEquals(Instant.parse("2026-10-01T15:00:00Z"), saved.savedAt)
    }

    @Test
    fun `the snapshot records which TSE system its status came from`() = runTest {
        candidates.listFlow(ELECTION_ID, "SP", OfficeRules.SENATOR).value =
            CachedData(UiTestData.senators, Instant.EPOCH, isStale = false, source = DataSource.TSE_OPEN_DATA)
        val vm = viewModel(candidateId = 2)
        vm.uiState.test {
            expectMostRecentItem()
            vm.onSaveClick()
            assertEquals(PickState.SavedHere, expectMostRecentItem().pickState)
        }
        assertEquals(DataSource.TSE_OPEN_DATA, ballot.picks.value.single().source)
    }

    @Test
    fun `a save that can't be written is reported, and saving works again afterwards`() = runTest {
        ballot.writeFailure = IOException("ENOSPC")
        val vm = viewModel(candidateId = 2)
        vm.uiState.test {
            vm.onSaveClick()
            val failed = expectMostRecentItem()
            assertEquals(DetailMessage.Failed(AppError.Storage), failed.message)
            assertEquals(false, failed.isSaving)
            assertEquals(PickState.NotSaved, failed.pickState)
            vm.onMessageShown()
            ballot.writeFailure = null
            vm.onSaveClick()
            assertEquals(PickState.SavedHere, expectMostRecentItem().pickState)
        }
    }

    @Test
    fun `while saved picks can't be read, saving waits behind the banner`() = runTest {
        ballot.unavailable.value = true
        val vm = viewModel(candidateId = 2)
        vm.uiState.test {
            assertTrue(expectMostRecentItem().picksUnavailable)
            vm.onSaveClick()
            vm.onRetryRead()
        }
        assertTrue(ballot.picks.value.isEmpty())
        assertEquals(1, ballot.retryReadCalls)
    }

    @Test
    fun `saving a candidate who is not fit shows the TSE status verbatim`() = runTest {
        val vm = viewModel(candidateId = 3)
        vm.uiState.test {
            vm.onSaveClick()
            assertEquals(DetailMessage.Saved(statusNotice = "Indeferido"), expectMostRecentItem().message)
        }
    }

    @Test
    fun `an occupied vote asks before replacing`() = runTest {
        ballot.picks.value = listOf(UiTestData.pick(UiTestData.senators[0], slot = 1))
        val vm = viewModel(candidateId = 2, slot = 1)
        vm.uiState.test {
            vm.onSaveClick()
            val asking = expectMostRecentItem()
            assertEquals("Elisa Teste", asking.confirmReplace?.ballotName)
            assertEquals(1, ballot.picks.value.size)

            vm.onConfirmReplace()
            val replaced = expectMostRecentItem()
            assertNull(replaced.confirmReplace)
            assertEquals(PickState.SavedHere, replaced.pickState)
        }
        assertEquals(listOf(2L), ballot.picks.value.map { it.candidateId })
    }

    @Test
    fun `the same candidate is refused in the other Senate vote`() = runTest {
        ballot.picks.value = listOf(UiTestData.pick(UiTestData.senators[1], slot = 1))
        val vm = viewModel(candidateId = 2, slot = 2)
        vm.uiState.test {
            assertEquals(PickState.InOtherSlot(1), expectMostRecentItem().pickState)
            vm.onSaveClick()
            assertEquals(DetailMessage.Duplicate(otherSlot = 1), expectMostRecentItem().message)
        }
        assertEquals(1, ballot.picks.value.size)
    }

    @Test
    fun `remove can be undone`() = runTest {
        val pick = UiTestData.pick(UiTestData.senators[1], slot = 1)
        ballot.picks.value = listOf(pick)
        val vm = viewModel(candidateId = 2)
        vm.uiState.test {
            vm.onRemoveClick()
            val removed = expectMostRecentItem()
            assertEquals(DetailMessage.Removed(pick), removed.message)
            assertEquals(PickState.NotSaved, removed.pickState)
            vm.onUndoRemove(pick)
            assertEquals(PickState.SavedHere, expectMostRecentItem().pickState)
        }
    }

    @Test
    fun `when the TSE blocks the detail, the cached list data stays usable`() = runTest {
        candidates.refreshDetailResult = AppResult.Failure(AppError.Blocked(403))
        val vm = viewModel(candidateId = 4)
        vm.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(DetailContent.Loaded, state.content)
            assertEquals("Carla Modelo", state.candidate?.ballotName)
            assertEquals(AppError.Blocked(403), state.detailError)
            assertTrue(state.officialPageUrl.startsWith("https://divulgacandcontas.tse.jus.br/divulga/#/candidato/SP/SP/"))
        }
    }

    @Test
    fun `the detail wins over the list and its TSE link is used`() = runTest {
        val listCandidate = UiTestData.senators[3]
        candidates.detailFlow(ELECTION_ID, 4).value = CachedData(
            value = CandidateDetail(
                candidate = listCandidate.copy(status = listCandidate.status.copy(registration = "Deferido")),
                coalitionType = "Partido Isolado",
                coalitionComposition = null,
                runningMates = emptyList(),
                officialPageUrl = "https://divulgacandcontas.tse.jus.br/divulga/#/candidato/2026/20322002026/SP/4",
                photoPublishable = false,
                lastUpdate = null,
            ),
            fetchedAt = Instant.EPOCH,
            isStale = false,
        )
        val vm = viewModel(candidateId = 4)
        vm.uiState.test {
            val state = expectMostRecentItem()
            assertEquals("Deferido", state.candidate?.status?.registration)
            assertEquals("https://divulgacandcontas.tse.jus.br/divulga/#/candidato/2026/20322002026/SP/4", state.officialPageUrl)
            assertNull(state.photoUrl)
        }
    }

    @Test
    fun `nothing cached and the detail missing shows the error`() = runTest {
        candidates.refreshDetailResult = AppResult.Failure(AppError.NotFound)
        val vm = viewModel(candidateId = 999)
        vm.uiState.test {
            assertEquals(DetailContent.Failed(AppError.NotFound), expectMostRecentItem().content)
        }
    }

    @Test
    fun `picks cannot change once the round date has passed`() = runTest {
        val vm = viewModel(candidateId = 2, clockAt = "2026-10-06T15:00:00Z")
        vm.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(false, state.canEdit)
            vm.onSaveClick()
            assertTrue(ballot.picks.value.isEmpty())
        }
    }
}
