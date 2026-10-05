package com.veronezzi.colaeleitoral.ui.screens.cola

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.ui.navigation.ColaExportRoute
import com.veronezzi.colaeleitoral.ui.testing.FakeBallotRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeElectionRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeSettingsRepository
import com.veronezzi.colaeleitoral.ui.testing.MainDispatcherRule
import com.veronezzi.colaeleitoral.ui.testing.UiTestData
import com.veronezzi.colaeleitoral.ui.testing.UiTestData.ELECTION_ID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

/** S17: writing the image to share goes through the ViewModel and never crashes the screen. */
class ColaExportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val elections = FakeElectionRepository(listOf(UiTestData.election2026)).apply {
        offices.value = offices.value.copy(value = UiTestData.spOffices)
    }
    private val ballot = FakeBallotRepository(listOf(UiTestData.pick(UiTestData.senators[1], slot = 1)))
    private val settings = FakeSettingsRepository(UserSettings(location = UiTestData.sp, acceptedDisclaimerVersion = 1))
    private val content = ColaContent(title = "Cola", lines = emptyList(), footer = emptyList())

    private fun viewModel(writer: suspend (ColaContent) -> File) = ColaExportViewModel(
        route = ColaExportRoute(ELECTION_ID, round = 1),
        savedStateHandle = SavedStateHandle(),
        electionRepository = elections,
        ballotRepository = ballot,
        settingsRepository = settings,
        imageWriter = ColaImageWriter(writer),
    )

    @Test
    fun `an image that can't be written shows a message and nothing is shared`() = runTest {
        val vm = viewModel { throw IOException("ENOSPC") }
        vm.uiState.test {
            vm.onShareConfirmed(content)
            val state = expectMostRecentItem()
            assertEquals(ColaMessage.ImageFailed, state.message)
            assertFalse(state.isExporting)
            assertNull(state.shareFile)
            vm.onMessageShown()
            assertNull(expectMostRecentItem().message)
        }
    }

    @Test
    fun `a written image goes to the share sheet once`() = runTest {
        val file = File("minha-cola.png")
        val vm = viewModel { file }
        vm.uiState.test {
            vm.onShareConfirmed(content)
            assertEquals(file, expectMostRecentItem().shareFile)
            vm.onShareHandled(delivered = true)
            val shared = expectMostRecentItem()
            assertNull(shared.shareFile)
            assertNull(shared.message)
        }
    }

    @Test
    fun `no app to receive the image is reported`() = runTest {
        val vm = viewModel { File("minha-cola.png") }
        vm.uiState.test {
            vm.onShareConfirmed(content)
            vm.onShareHandled(delivered = false)
            assertEquals(ColaMessage.ShareUnavailable, expectMostRecentItem().message)
        }
    }

    @Test
    fun `unreadable picks show the banner on the cola too`() = runTest {
        ballot.unavailable.value = true
        val vm = viewModel { File("minha-cola.png") }
        vm.uiState.test {
            assertTrue(expectMostRecentItem().picksUnavailable)
        }
    }
}
