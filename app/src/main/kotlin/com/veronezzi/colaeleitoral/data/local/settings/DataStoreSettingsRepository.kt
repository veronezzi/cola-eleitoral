package com.veronezzi.colaeleitoral.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences in a plain DataStore (no political data here; the file is excluded from backup by
 * the manifest rules). An unreadable file yields the defaults instead of crashing.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<UserSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun setLocation(location: VoterLocation) = edit { prefs ->
        prefs[UF] = location.uf
        val municipality = location.municipality
        if (municipality != null) {
            prefs[MUNICIPALITY_CODE] = municipality.code
            prefs[MUNICIPALITY_NAME] = municipality.name
        } else {
            prefs.remove(MUNICIPALITY_CODE)
            prefs.remove(MUNICIPALITY_NAME)
        }
    }

    override suspend fun completeOnboarding(version: Int) = edit { prefs ->
        prefs[ONBOARDING_COMPLETED] = true
        prefs[ACCEPTED_DISCLAIMER_VERSION] = version
    }

    override suspend fun setReminderEnabled(enabled: Boolean) = edit { it[REMINDER_ENABLED] = enabled }

    override suspend fun setAppLockEnabled(enabled: Boolean) = edit { it[APP_LOCK_ENABLED] = enabled }

    override suspend fun setSecureScreens(enabled: Boolean) = edit { it[SECURE_SCREENS] = enabled }

    override suspend fun clear() = edit { it.clear() }

    private suspend fun edit(change: (MutablePreferences) -> Unit) {
        dataStore.edit { prefs -> change(prefs) }
    }

    private fun Preferences.toSettings(): UserSettings {
        val defaults = UserSettings()
        val location = this[UF]?.let { uf ->
            val municipalityCode = this[MUNICIPALITY_CODE]
            VoterLocation(
                uf = uf,
                municipality = municipalityCode?.let { code ->
                    ElectoralUnit(code = code, name = this[MUNICIPALITY_NAME] ?: code, uf = uf, isMunicipality = true)
                },
            )
        }
        return UserSettings(
            location = location,
            onboardingCompleted = this[ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
            acceptedDisclaimerVersion = this[ACCEPTED_DISCLAIMER_VERSION] ?: defaults.acceptedDisclaimerVersion,
            reminderEnabled = this[REMINDER_ENABLED] ?: defaults.reminderEnabled,
            appLockEnabled = this[APP_LOCK_ENABLED] ?: defaults.appLockEnabled,
            secureScreens = this[SECURE_SCREENS] ?: defaults.secureScreens,
        )
    }

    companion object {
        /** DataStore file name, in `filesDir/datastore/`. */
        const val FILE_NAME = "user_settings"

        private val UF = stringPreferencesKey("location_uf")
        private val MUNICIPALITY_CODE = stringPreferencesKey("location_municipality_code")
        private val MUNICIPALITY_NAME = stringPreferencesKey("location_municipality_name")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val ACCEPTED_DISCLAIMER_VERSION = intPreferencesKey("accepted_disclaimer_version")
        private val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val SECURE_SCREENS = booleanPreferencesKey("secure_screens")
    }
}
