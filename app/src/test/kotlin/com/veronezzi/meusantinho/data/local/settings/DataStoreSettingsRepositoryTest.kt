package com.veronezzi.meusantinho.data.local.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.veronezzi.meusantinho.domain.model.ElectoralUnit
import com.veronezzi.meusantinho.domain.model.UserSettings
import com.veronezzi.meusantinho.domain.model.VoterLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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
}
