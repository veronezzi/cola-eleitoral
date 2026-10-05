package com.veronezzi.colaeleitoral.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.UserSettings
import com.veronezzi.colaeleitoral.domain.repository.BallotRepository
import com.veronezzi.colaeleitoral.domain.repository.ElectionRepository
import com.veronezzi.colaeleitoral.domain.repository.SettingsRepository
import com.veronezzi.colaeleitoral.ui.common.AppClock
import com.veronezzi.colaeleitoral.ui.common.ElectionSelection
import com.veronezzi.colaeleitoral.ui.common.resolveElection
import com.veronezzi.colaeleitoral.ui.common.tryLocalWrite
import com.veronezzi.colaeleitoral.ui.navigation.BallotRoute
import com.veronezzi.colaeleitoral.ui.screens.onboarding.DISCLAIMER_VERSION
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

enum class StartDestination { ONBOARDING, LOCATION, HOME }

sealed interface AppUiState {
    /** Settings not read yet: the splash screen stays. */
    data object Loading : AppUiState

    data class Ready(
        val startDestination: StartDestination,
        val isLocked: Boolean,
        val secureScreens: Boolean,
        /** Target of the "Minha cola" tab; null until an election is known. */
        val ballotTarget: BallotRoute?,
        /** Saved picks could not be decrypted and were discarded: show the one-time notice. */
        val picksLost: Boolean,
        /** Turning the lock off from the lock screen could not be saved (disk full). */
        val lockTurnOffFailed: Boolean = false,
    ) : AppUiState
}

private enum class LockState { UNKNOWN, LOCKED, UNLOCKED }

/**
 * App-wide state: the start destination (decided once, from the first settings read), the
 * optional lock gate and the target of the "Minha cola" tab. The lock closes on every cold
 * start and when the app comes back after [LOCK_AFTER] in the background (ARCHITECTURE.md 4.10).
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    electionRepository: ElectionRepository,
    private val ballotRepository: BallotRepository,
    electionSelection: ElectionSelection,
    private val clock: AppClock,
) : ViewModel() {
    private val lockState = MutableStateFlow(LockState.UNKNOWN)
    private val lockTurnOffFailed = MutableStateFlow(false)
    private var startDestination = StartDestination.ONBOARDING
    private var backgroundedAt: Instant? = null

    private val settings: StateFlow<UserSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val ballotTarget = combine(
        electionRepository.observeElections(),
        electionSelection.selectedElectionId,
    ) { elections, selected ->
        resolveElection(elections.value, selected, clock.todayInBrasilia())
            ?.let { BallotRoute(it.election.id, it.round.number) }
    }.distinctUntilChanged()

    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.settings,
        lockState,
        ballotTarget,
        ballotRepository.observePicksLost(),
        lockTurnOffFailed,
    ) { settings, lock, target, picksLost, turnOffFailed ->
        if (lock == LockState.UNKNOWN) {
            AppUiState.Loading
        } else {
            AppUiState.Ready(
                startDestination = startDestination,
                isLocked = lock == LockState.LOCKED && settings.appLockEnabled,
                secureScreens = settings.secureScreens,
                ballotTarget = target,
                picksLost = picksLost,
                lockTurnOffFailed = turnOffFailed,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState.Loading)

    init {
        viewModelScope.launch {
            val first = settingsRepository.settings.first()
            startDestination = startOf(first)
            lockState.value = if (first.appLockEnabled) LockState.LOCKED else LockState.UNLOCKED
        }
    }

    fun onAppBackgrounded() {
        backgroundedAt = clock.now()
    }

    fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val lockEnabled = settings.value?.appLockEnabled == true
        if (lockEnabled && Duration.between(since, clock.now()) >= LOCK_AFTER) {
            lockState.value = LockState.LOCKED
        }
    }

    fun onUnlocked() {
        lockState.value = LockState.UNLOCKED
    }

    /**
     * The device no longer has a screen lock, so the prompt can never succeed: the user chose, after
     * a warning, to turn the app lock off instead of clearing the app's data (and losing the picks).
     */
    fun onTurnOffLock() {
        viewModelScope.launch {
            val turnedOff = settingsRepository.setAppLockEnabled(false) is AppResult.Success
            lockTurnOffFailed.value = !turnedOff
            if (turnedOff) lockState.value = LockState.UNLOCKED
        }
    }

    fun onPicksLostAcknowledged() {
        viewModelScope.launch { tryLocalWrite { ballotRepository.acknowledgePicksLost() } }
    }

    companion object {
        val LOCK_AFTER: Duration = Duration.ofSeconds(30)

        fun startOf(settings: UserSettings): StartDestination = when {
            settings.acceptedDisclaimerVersion < DISCLAIMER_VERSION -> StartDestination.ONBOARDING
            settings.location == null -> StartDestination.LOCATION
            else -> StartDestination.HOME
        }
    }
}
