package com.veronezzi.colaeleitoral.domain.repository

import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import kotlinx.coroutines.flow.Flow

/**
 * The writes never throw on storage failures (disk full, I/O error): they return
 * `AppResult.Failure(AppError.Storage)` and leave the previous value in place. Callers may ignore
 * the result when there is nothing useful to tell the user.
 */
interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setLocation(location: VoterLocation): AppResult<Unit>

    /** Marks the first-run flow as done and records the accepted disclaimer [version]. */
    suspend fun completeOnboarding(version: Int): AppResult<Unit>

    suspend fun setReminderEnabled(enabled: Boolean): AppResult<Unit>

    suspend fun setAppLockEnabled(enabled: Boolean): AppResult<Unit>

    suspend fun setSecureScreens(enabled: Boolean): AppResult<Unit>

    /** Resets every preference (part of "Apagar meus dados"). */
    suspend fun clear(): AppResult<Unit>
}
