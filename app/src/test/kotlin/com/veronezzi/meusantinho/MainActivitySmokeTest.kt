package com.veronezzi.meusantinho

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Build smoke test: Robolectric boots the Hilt application, launches [MainActivity] with the
 * splash theme and renders the Compose placeholder from the merged pt-BR resources.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsAppName() {
        composeRule.onNodeWithText("Meu Santinho").assertIsDisplayed()
    }
}
