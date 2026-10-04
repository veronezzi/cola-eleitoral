package com.veronezzi.colaeleitoral.ui.screens

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.veronezzi.colaeleitoral.domain.model.CachedData
import com.veronezzi.colaeleitoral.domain.model.ElectoralUnit
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.ui.common.ElectionSelection
import com.veronezzi.colaeleitoral.ui.navigation.LocationRoute
import com.veronezzi.colaeleitoral.ui.screens.location.LocationStep
import com.veronezzi.colaeleitoral.ui.screens.location.LocationViewModel
import com.veronezzi.colaeleitoral.ui.screens.onboarding.DISCLAIMER_VERSION
import com.veronezzi.colaeleitoral.ui.screens.onboarding.OnboardingNext
import com.veronezzi.colaeleitoral.ui.screens.onboarding.OnboardingViewModel
import com.veronezzi.colaeleitoral.ui.screens.settings.SettingsConfirmation
import com.veronezzi.colaeleitoral.ui.screens.settings.SettingsViewModel
import com.veronezzi.colaeleitoral.ui.testing.FakeBallotRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeElectionRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeReminderScheduler
import com.veronezzi.colaeleitoral.ui.testing.FakeSettingsRepository
import com.veronezzi.colaeleitoral.ui.testing.MainDispatcherRule
import com.veronezzi.colaeleitoral.ui.testing.UiTestData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FirstRunAndSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val elections = FakeElectionRepository(listOf(UiTestData.election2026))

    @Test
    fun `accepting the notice records its version and goes to the place`() = runTest {
        val vm = OnboardingViewModel(settings)
        vm.onAccept()
        assertEquals(DISCLAIMER_VERSION, settings.state.value.acceptedDisclaimerVersion)
        assertEquals(OnboardingNext.LOCATION, vm.uiState.value.next)
        vm.onNavigated()
        assertNull(vm.uiState.value.next)
    }

    private fun locationViewModel() = LocationViewModel(
        route = LocationRoute(),
        savedStateHandle = SavedStateHandle(),
        settingsRepository = settings,
        electionRepository = elections,
        electionSelection = ElectionSelection(),
        clock = UiTestData.clockAt(),
    )

    @Test
    fun `the DF and voters abroad are never asked for a municipality`() = runTest {
        val vm = locationViewModel()
        vm.uiState.test {
            vm.onUfSelected("DF")
            assertTrue(expectMostRecentItem().done)
        }
        assertEquals(VoterLocation("DF"), settings.state.value.location)
    }

    @Test
    fun `a UF leads to an accent-insensitive municipality search`() = runTest {
        elections.municipalityFlow("AC").value = CachedData(
            listOf(
                ElectoralUnit("01120", "ACRELÂNDIA", "AC", isMunicipality = true),
                ElectoralUnit("01570", "BRASILÉIA", "AC", isMunicipality = true),
            ),
            Instant.EPOCH,
            isStale = false,
        )
        val vm = locationViewModel()
        vm.uiState.test {
            vm.onUfSelected("AC")
            val step = expectMostRecentItem()
            assertEquals(LocationStep.MUNICIPALITY, step.step)
            assertFalse(step.municipalityRequired)
            assertEquals(2, step.municipalities.size)

            vm.onQueryChange("brasileia")
            val found = expectMostRecentItem().municipalities
            assertEquals(listOf("01570"), found.map { it.code })

            vm.onMunicipalitySelected(found.single())
            assertTrue(expectMostRecentItem().done)
        }
        assertEquals("01570", settings.state.value.location?.municipality?.code)
    }

    @Test
    fun `deleting my data clears picks, reminders, cache and preferences`() = runTest {
        settings.state.value = UserSettings(location = UiTestData.sp, acceptedDisclaimerVersion = 1, reminderEnabled = true)
        val ballot = FakeBallotRepository(listOf(UiTestData.pick(UiTestData.senators[0])))
        val reminders = FakeReminderScheduler()
        val selection = ElectionSelection().apply { select(UiTestData.ELECTION_ID) }
        var imagesCleared = false
        val vm = SettingsViewModel(settings, ballot, elections, reminders, selection, UiTestData.clockAt())
        vm.uiState.test {
            vm.onConfirmationRequested(SettingsConfirmation.DELETE_ALL)
            assertEquals(SettingsConfirmation.DELETE_ALL, expectMostRecentItem().confirmation)
            vm.onConfirmed { imagesCleared = true }
            assertTrue(expectMostRecentItem().dataDeleted)
        }
        assertTrue(ballot.deletedAll)
        assertTrue(reminders.cancelledAll)
        assertTrue(elections.cacheCleared)
        assertTrue(imagesCleared)
        assertEquals(UserSettings(), settings.state.value)
        assertNull(selection.selectedElectionId.value)
    }

    @Test
    fun `turning the reminder on and off schedules and cancels`() = runTest {
        val reminders = FakeReminderScheduler()
        val vm = SettingsViewModel(settings, FakeBallotRepository(), elections, reminders, ElectionSelection(), UiTestData.clockAt())
        vm.onReminderEnabled()
        assertTrue(settings.state.value.reminderEnabled)
        assertEquals(listOf(Triple(UiTestData.ELECTION_ID, Round.FIRST, LocalDate.of(2026, 10, 4))), reminders.scheduled)
        vm.onReminderDisabled()
        assertFalse(settings.state.value.reminderEnabled)
        assertTrue(reminders.cancelledAll)
    }
}
