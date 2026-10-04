package com.veronezzi.meusantinho.ui.screens

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veronezzi.meusantinho.domain.model.OfficeRules
import com.veronezzi.meusantinho.domain.model.UserSettings
import com.veronezzi.meusantinho.ui.common.UiDispatchers
import com.veronezzi.meusantinho.ui.navigation.CandidateDetailRoute
import com.veronezzi.meusantinho.ui.navigation.CandidateListRoute
import com.veronezzi.meusantinho.ui.screens.candidates.CandidateListActions
import com.veronezzi.meusantinho.ui.screens.candidates.CandidateListScreen
import com.veronezzi.meusantinho.ui.screens.candidates.CandidateListViewModel
import com.veronezzi.meusantinho.ui.screens.candidates.SEARCH_TEST_TAG
import com.veronezzi.meusantinho.ui.screens.detail.CandidateDetailScreen
import com.veronezzi.meusantinho.ui.screens.detail.CandidateDetailViewModel
import com.veronezzi.meusantinho.ui.screens.detail.REMOVE_BUTTON_TAG
import com.veronezzi.meusantinho.ui.screens.detail.SAVE_BUTTON_TAG
import com.veronezzi.meusantinho.ui.testing.FakeBallotRepository
import com.veronezzi.meusantinho.ui.testing.FakeCandidateRepository
import com.veronezzi.meusantinho.ui.testing.FakeElectionRepository
import com.veronezzi.meusantinho.ui.testing.FakeSettingsRepository
import com.veronezzi.meusantinho.ui.testing.UiTestData
import com.veronezzi.meusantinho.ui.testing.UiTestData.ELECTION_ID
import com.veronezzi.meusantinho.ui.theme.MeuSantinhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Compose UI tests on Robolectric with the real ViewModels and in-memory repositories: search and
 * filters narrow the list; saving a pick gives feedback and refuses a repeated Senate vote.
 * A plain Application keeps the data layer (and Hilt) out of these tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class CandidateScreensUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private val candidates = FakeCandidateRepository().apply {
        setCandidates(ELECTION_ID, "SP", OfficeRules.SENATOR, UiTestData.senators)
    }
    private val elections = FakeElectionRepository(listOf(UiTestData.election2026)).apply {
        offices.value = offices.value.copy(value = UiTestData.spOffices)
    }
    private val ballot = FakeBallotRepository()
    private val settings = FakeSettingsRepository(UserSettings(location = UiTestData.sp, acceptedDisclaimerVersion = 1))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val isFilterChip = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    @Test
    fun searchAndPartyFilterNarrowTheList() {
        val vm = CandidateListViewModel(
            route = CandidateListRoute(ELECTION_ID, 2026, "SP", OfficeRules.SENATOR, round = 1, slot = 1),
            savedStateHandle = SavedStateHandle(),
            candidateRepository = candidates,
            electionRepository = elections,
            settingsRepository = settings,
            ballotRepository = ballot,
            dispatchers = UiDispatchers(dispatcher),
        )
        val actions = CandidateListActions(
            onBack = {},
            onCandidateClick = {},
            onQueryChange = vm::onQueryChange,
            onPartyToggled = vm::onPartyToggled,
            onStatusToggled = vm::onStatusToggled,
            onOnlySecondRoundChange = vm::onOnlySecondRoundChange,
            onSortChange = vm::onSortChange,
            onClearFilters = vm::onClearFilters,
            onOfficeSelected = {},
            onRefresh = vm::onRefresh,
        )
        composeRule.setContent {
            val state by vm.uiState.collectAsState()
            MeuSantinhoTheme(dynamicColor = false) { CandidateListScreen(state = state, actions = actions) }
        }
        composeRule.onNodeWithText("Mostrando 4 de 4 candidaturas").assertIsDisplayed()
        composeRule.onNodeWithText("Ana Exemplo").assertIsDisplayed()

        // Search: debounced in the ViewModel (250 ms of virtual time).
        composeRule.onNodeWithTag(SEARCH_TEST_TAG).performTextInput("bruno")
        dispatcher.scheduler.advanceTimeBy(CandidateListViewModel.SEARCH_DEBOUNCE_MILLIS + 50)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Bruno Fictício").assertIsDisplayed()
        composeRule.onNodeWithText("Ana Exemplo").assertDoesNotExist()
        composeRule.onNodeWithText("Mostrando 1 de 4 candidaturas").assertIsDisplayed()

        // Clear the search, then filter by party in the bottom sheet.
        composeRule.onNodeWithContentDescription("Limpar busca").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Mostrando 4 de 4 candidaturas").assertIsDisplayed()
        composeRule.onNodeWithText("Filtros e ordem").performClick()
        composeRule.onNode(hasText("PMO") and isFilterChip).performScrollTo().performClick()
        composeRule.onNodeWithText("Ver 1 resultado").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Carla Modelo").assertIsDisplayed()
        composeRule.onNodeWithText("Elisa Teste").assertDoesNotExist()
        composeRule.onNodeWithText("Mostrando 1 de 4 candidaturas").assertIsDisplayed()
    }

    private fun detailViewModel(candidateId: Long, slot: Int) = CandidateDetailViewModel(
        route = CandidateDetailRoute(ELECTION_ID, 2026, "SP", OfficeRules.SENATOR, candidateId, round = 1, slot = slot),
        candidateRepository = candidates,
        electionRepository = elections,
        ballotRepository = ballot,
        settingsRepository = settings,
        clock = UiTestData.clockAt(),
    )

    private fun showDetail(vm: CandidateDetailViewModel) {
        composeRule.setContent {
            val state by vm.uiState.collectAsState()
            MeuSantinhoTheme(dynamicColor = false) {
                CandidateDetailScreen(
                    state = state,
                    onBack = null,
                    onSave = vm::onSaveClick,
                    onRemove = vm::onRemoveClick,
                    onConfirmReplace = vm::onConfirmReplace,
                    onDismissReplace = vm::onDismissReplace,
                    onUndoRemove = vm::onUndoRemove,
                    onMessageShown = vm::onMessageShown,
                    onRetry = vm::onRefresh,
                    onOpenBallot = {},
                )
            }
        }
    }

    @Test
    fun savingAPickConfirmsItAndOffersRemoval() {
        showDetail(detailViewModel(candidateId = 2, slot = 1))
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(SAVE_BUTTON_TAG).performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("Salvo no seu santinho: Senador: 1ª vaga.").assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true

        composeRule.onNodeWithText("No seu santinho: Senador: 1ª vaga").assertExists()
        composeRule.onNodeWithTag(REMOVE_BUTTON_TAG).assertExists()
        assertEquals(listOf(2L to 1), ballot.picks.value.map { it.candidateId to it.slot })
    }

    @Test
    fun theSameSenatorIsNotOfferedForTheOtherVote() {
        ballot.picks.value = listOf(UiTestData.pick(UiTestData.senators[1], slot = 1))
        showDetail(detailViewModel(candidateId = 2, slot = 2))
        composeRule.onNodeWithText("Este candidato já está no seu santinho, no outro voto para Senador (1ª vaga).")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SAVE_BUTTON_TAG).assertDoesNotExist()
        assertEquals(1, ballot.picks.value.size)
    }
}
