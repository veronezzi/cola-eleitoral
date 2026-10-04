package com.veronezzi.colaeleitoral.domain.repository

import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setLocation(location: VoterLocation)

    /** Marks the first-run flow as done and records the accepted disclaimer [version]. */
    suspend fun completeOnboarding(version: Int)

    suspend fun setReminderEnabled(enabled: Boolean)

    suspend fun setAppLockEnabled(enabled: Boolean)

    suspend fun setSecureScreens(enabled: Boolean)

    /** Resets every preference (part of "Apagar meus dados"). */
    suspend fun clear()
}
