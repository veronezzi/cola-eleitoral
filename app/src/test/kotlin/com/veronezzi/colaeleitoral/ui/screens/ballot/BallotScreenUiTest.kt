package com.veronezzi.colaeleitoral.ui.screens.ballot

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.ui.common.buildBallotSlots
import com.veronezzi.colaeleitoral.ui.testing.UiTestData
import com.veronezzi.colaeleitoral.ui.testing.UiTestData.ELECTION_ID
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** "Minha cola": buttons repeated on every vote say which vote they act on (S14); unreadable picks get a banner. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h2400dp")
class BallotScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pick = UiTestData.pick(UiTestData.senators[1], slot = 1)

    private fun show(picksUnavailable: Boolean = false, onRemove: (BallotPick) -> Unit = {}, onRetryRead: () -> Unit = {}) {
        val state = BallotUiState(
            isLoading = false,
            electionId = ELECTION_ID,
            electionYear = 2026,
            electionName = UiTestData.election2026.name,
            entries = buildBallotSlots(UiTestData.spOffices, listOf(pick)).map { BallotEntry(it) },
            picksUnavailable = picksUnavailable,
        )
        composeRule.setContent {
            ColaEleitoralTheme(dynamicColor = false) {
                BallotScreen(
                    state = state,
                    actions = BallotActions(onChoose = { _, _, _, _ -> }, onOpenPick = {}, onExport = { _, _ -> }),
                    onRoundSelected = {},
                    onRemove = onRemove,
                    onUndoRemove = {},
                    onClearRequested = {},
                    onClearConfirmed = {},
                    onClearDismissed = {},
                    onMessageShown = {},
                    onRetryRead = onRetryRead,
                )
            }
        }
    }

    @Test
    fun repeatedButtonsNameTheirVote() {
        var removed: BallotPick? = null
        show(onRemove = { removed = it })
        composeRule.onNodeWithContentDescription("Remover, Senador, primeira vaga").performClick()
        assertEquals(pick, removed)
        composeRule.onNodeWithContentDescription("Trocar, Senador, primeira vaga").assertExists()
        composeRule.onNodeWithContentDescription("Detalhes, Senador, primeira vaga").assertExists()
        composeRule.onNodeWithContentDescription("Escolher, Senador, segunda vaga").assertExists()
        composeRule.onNodeWithContentDescription("Escolher, Presidente").assertExists()
    }

    @Test
    fun unreadablePicksShowABannerWithRetry() {
        var retries = 0
        show(picksUnavailable = true, onRetryRead = { retries += 1 })
        composeRule.onNodeWithText("Não foi possível ler suas escolhas agora").assertIsDisplayed()
        composeRule.onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, retries)
    }
}
