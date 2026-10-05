package com.veronezzi.colaeleitoral.ui.navigation

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A7: the content slot (the NavHost) stays in composition whatever the chrome around it, so
 * leaving the first-run screens or resizing the window never resets the back stack's state.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AppNavigationLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theContentSurvivesEveryChromeChange() {
        var chrome by mutableStateOf(NavigationChrome.NONE)
        var created = 0
        composeRule.setContent {
            ColaEleitoralTheme(dynamicColor = false) {
                AppNavigationLayout(chrome = chrome, selected = TopLevelDestination.HOME, ballotEnabled = true, onSelect = {}) {
                    val instance = remember { ++created }
                    Text("conteúdo $instance")
                }
            }
        }
        for (next in listOf(NavigationChrome.BAR, NavigationChrome.RAIL, NavigationChrome.NONE, NavigationChrome.BAR)) {
            chrome = next
            composeRule.waitForIdle()
            composeRule.onNodeWithText("conteúdo 1").assertExists()
        }
        composeRule.onNodeWithText("Minha cola").assertExists()
        assertEquals(1, created)
    }
}
