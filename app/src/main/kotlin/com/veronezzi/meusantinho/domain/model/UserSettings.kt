package com.veronezzi.meusantinho.domain.model

/**
 * Preferences (plain DataStore; no political data here).
 *
 * @property acceptedDisclaimerVersion version of the "not affiliated with the TSE" notice the user
 * accepted; 0 means never. Bump it to show the notice again after a wording change.
 * @property secureScreens hides ballot screens from screenshots and the recents preview.
 */
data class UserSettings(
    val location: VoterLocation? = null,
    val onboardingCompleted: Boolean = false,
    val acceptedDisclaimerVersion: Int = 0,
    val reminderEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val secureScreens: Boolean = true,
)
