package com.veronezzi.colaeleitoral.ui

import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.ui.common.AppClock
import com.veronezzi.colaeleitoral.ui.common.ElectionSelection
import com.veronezzi.colaeleitoral.ui.navigation.BallotRoute
import com.veronezzi.colaeleitoral.ui.screens.onboarding.DISCLAIMER_VERSION
import com.veronezzi.colaeleitoral.ui.testing.FakeBallotRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeElectionRepository
import com.veronezzi.colaeleitoral.ui.testing.FakeSettingsRepository
import com.veronezzi.colaeleitoral.ui.testing.MainDispatcherRule
import com.veronezzi.colaeleitoral.ui.testing.UiTestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class AppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Mutable clock to simulate time spent in the background. */
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = instant
    }

    private val clock = MutableClock(Instant.parse("2026-10-01T15:00:00Z"))
    private val ballot = FakeBallotRepository()

    private fun viewModel(settings: UserSettings) = AppViewModel(
        settingsRepository = FakeSettingsRepository(settings),
        electionRepository = FakeElectionRepository(listOf(UiTestData.election2026)),
        ballotRepository = ballot,
        electionSelection = ElectionSelection(),
        clock = AppClock(clock),
    )

    private fun AppViewModel.ready() = uiState.value as AppUiState.Ready

    @Test
    fun `first run starts at the notice, then the place, then home`() {
        assertEquals(StartDestination.ONBOARDING, viewModel(UserSettings()).ready().startDestination)
        assertEquals(
            StartDestination.LOCATION,
            viewModel(UserSettings(acceptedDisclaimerVersion = DISCLAIMER_VERSION)).ready().startDestination,
        )
        assertEquals(
            StartDestination.HOME,
            viewModel(UserSettings(acceptedDisclaimerVersion = DISCLAIMER_VERSION, location = UiTestData.sp)).ready().startDestination,
        )
    }

    @Test
    fun `an outdated notice is shown again`() {
        val settings = UserSettings(acceptedDisclaimerVersion = DISCLAIMER_VERSION - 1, location = UiTestData.sp)
        assertEquals(StartDestination.ONBOARDING, viewModel(settings).ready().startDestination)
    }

    @Test
    fun `the lock closes on start and after 30 seconds in the background only`() {
        val vm = viewModel(UserSettings(acceptedDisclaimerVersion = DISCLAIMER_VERSION, location = UiTestData.sp, appLockEnabled = true))
        assertTrue(vm.ready().isLocked)
        vm.onUnlocked()
        assertFalse(vm.ready().isLocked)

        vm.onAppBackgrounded()
        clock.instant = clock.instant.plus(Duration.ofSeconds(29))
        vm.onAppForegrounded()
        assertFalse(vm.ready().isLocked)

        vm.onAppBackgrounded()
        clock.instant = clock.instant.plus(AppViewModel.LOCK_AFTER)
        vm.onAppForegrounded()
        assertTrue(vm.ready().isLocked)
    }

    @Test
    fun `no lock when the option is off`() {
        val vm = viewModel(UserSettings(acceptedDisclaimerVersion = DISCLAIMER_VERSION, location = UiTestData.sp))
        vm.onAppBackgrounded()
        clock.instant = clock.instant.plus(Duration.ofMinutes(10))
        vm.onAppForegrounded()
        assertFalse(vm.ready().isLocked)
    }

    @Test
    fun `the ballot tab targets the current election and round`() {
        val vm = viewModel(UserSettings(acceptedDisclaimerVersion = DISCLAIMER_VERSION, location = UiTestData.sp))
        assertEquals(BallotRoute(UiTestData.ELECTION_ID, 1), vm.ready().ballotTarget)
    }

    @Test
    fun `lost picks are announced once`() {
        ballot.picksLost.value = true
        val vm = viewModel(UserSettings(acceptedDisclaimerVersion = DISCLAIMER_VERSION, location = UiTestData.sp))
        assertTrue(vm.ready().picksLost)
        vm.onPicksLostAcknowledged()
        assertFalse(vm.ready().picksLost)
    }
}
