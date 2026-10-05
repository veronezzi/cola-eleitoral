package com.veronezzi.colaeleitoral.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val repository by lazy {
        DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(temporaryFolder.root, "user_settings.preferences_pb") },
        )
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaultsProtectScreensAndKeepTheReminderOff() = runTest {
        assertEquals(UserSettings(), repository.settings.first())
    }

    @Test
    fun preferencesRoundTrip() = runTest {
        val municipality = ElectoralUnit(code = "01120", name = "ACRELÂNDIA", uf = "AC", isMunicipality = true)

        repository.setLocation(VoterLocation("AC", municipality))
        repository.completeOnboarding(version = 2)
        repository.setReminderEnabled(true)
        repository.setAppLockEnabled(true)
        repository.setSecureScreens(false)

        assertEquals(
            UserSettings(
                location = VoterLocation("AC", municipality),
                onboardingCompleted = true,
                acceptedDisclaimerVersion = 2,
                reminderEnabled = true,
                appLockEnabled = true,
                secureScreens = false,
            ),
            repository.settings.first(),
        )
    }

    @Test
    fun changingToAUfWithoutMunicipalityDropsTheOldOne() = runTest {
        repository.setLocation(VoterLocation("AC", ElectoralUnit("01120", "ACRELÂNDIA", "AC", isMunicipality = true)))

        repository.setLocation(VoterLocation(ElectoralUnit.ABROAD_CODE))

        val location = repository.settings.first().location
        assertEquals(ElectoralUnit.ABROAD_CODE, location?.uf)
        assertNull(location?.municipality)
    }

    @Test
    fun clearResetsEverything() = runTest {
        repository.setLocation(VoterLocation("SP"))
        repository.setReminderEnabled(true)

        repository.clear()

        assertEquals(UserSettings(), repository.settings.first())
    }

    @Test
    fun aFailedWriteIsAStorageErrorInsteadOfACrash() = runTest {
        val fullDisk = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(emptyPreferences())

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException("No space left on device")
        }
        val repository = DataStoreSettingsRepository(fullDisk)

        assertEquals(AppResult.Failure(AppError.Storage), repository.setLocation(VoterLocation("SP")))
        assertEquals(AppResult.Failure(AppError.Storage), repository.setReminderEnabled(true))
        assertEquals(AppResult.Failure(AppError.Storage), repository.clear())
        assertEquals(UserSettings(), repository.settings.first())
    }
}
