package com.veronezzi.colaeleitoral.ui.screens.about

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * S6 and S8: the policy inside the app is docs/privacidade.md itself (embedded by the build), in
 * full, with real headings for TalkBack, and it names the developer.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class PrivacyPolicyScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theEmbeddedPolicyIsTheDocsFileVerbatim() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val embedded = context.resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
        assertEquals(File(PolicyMarkdownTest.POLICY_PATH).readText(), embedded)
    }

    @Test
    fun theWholePolicyRendersWithHeadingsAndTheDeveloper() {
        composeRule.setContent { ColaEleitoralTheme(dynamicColor = false) { PrivacyPolicyScreen(onBack = {}) } }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(PRIVACY_POLICY_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Desenvolvido por veronezzi, sem vínculo com partidos, candidatos ou governo").assertIsDisplayed()
        composeRule.onNodeWithText("Política de privacidade do app Cola Eleitoral")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

        val list = composeRule.onNodeWithTag(PRIVACY_POLICY_TEST_TAG)
        list.performScrollToNode(hasText("13. Contato"))
        composeRule.onNodeWithText("13. Contato").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        list.performScrollToNode(hasText("Ver a política na internet"))
        composeRule.onNodeWithText("Ver a política na internet").assertIsDisplayed()
    }
}
