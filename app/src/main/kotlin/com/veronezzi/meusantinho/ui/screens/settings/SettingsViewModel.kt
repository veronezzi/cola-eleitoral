package com.veronezzi.meusantinho.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veronezzi.meusantinho.domain.model.Round
import com.veronezzi.meusantinho.domain.model.VoterLocation
import com.veronezzi.meusantinho.domain.repository.BallotRepository
import com.veronezzi.meusantinho.domain.repository.ElectionRepository
import com.veronezzi.meusantinho.domain.repository.ReminderScheduler
import com.veronezzi.meusantinho.domain.repository.SettingsRepository
import com.veronezzi.meusantinho.ui.common.AppClock
import com.veronezzi.meusantinho.ui.common.ElectionSelection
import com.veronezzi.meusantinho.ui.common.resolveElection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SettingsConfirmation { CLEAR_DOWNLOADS, DELETE_ALL }

enum class SettingsMessage { DOWNLOADS_CLEARED, LOCK_UNAVAILABLE }

data class SettingsUiState(
    val location: VoterLocation? = null,
    val reminderEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val secureScreens: Boolean = true,
    val isBusy: Boolean = false,
    val confirmation: SettingsConfirmation? = null,
    val message: SettingsMessage? = null,
    /** "Apagar meus dados" finished: the app restarts at the first-run notice. */
    val dataDeleted: Boolean = false,
)

private data class SettingsTransient(
    val isBusy: Boolean = false,
    val confirmation: SettingsConfirmation? = null,
    val message: SettingsMessage? = null,
    val dataDeleted: Boolean = false,
)

/**
 * Settings: voting place, opt-in reminder, optional app lock, screen protection and data removal
 * (ARCHITECTURE.md 4.8, 4.10, 5.2 and 5.6). Permission and biometric prompts run in the UI; this
 * ViewModel records the outcome.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ballotRepository: BallotRepository,
    private val electionRepository: ElectionRepository,
    private val reminderScheduler: ReminderScheduler,
    private val electionSelection: ElectionSelection,
    private val clock: AppClock,
) : ViewModel() {
    private val transient = MutableStateFlow(SettingsTransient())

    val uiState: StateFlow<SettingsUiState> = combine(settingsRepository.settings, transient) { settings, transient ->
        SettingsUiState(
            location = settings.location,
            reminderEnabled = settings.reminderEnabled,
            appLockEnabled = settings.appLockEnabled,
            secureScreens = settings.secureScreens,
            isBusy = transient.isBusy,
            confirmation = transient.confirmation,
            message = transient.message,
            dataDeleted = transient.dataDeleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** After the rationale and the notification permission. Schedules the next first round. */
    fun onReminderEnabled() {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(true)
            val today = clock.todayInBrasilia()
            val context = resolveElection(
                electionRepository.observeElections().first().value,
                electionSelection.selectedElectionId.value,
                today,
            ) ?: return@launch
            val date = context.election.dateOf(Round.FIRST)
            if (date != null && !date.isBefore(today)) reminderScheduler.schedule(context.election, Round.FIRST, date)
            // The second round is scheduled by Home once the TSE marks a runoff for the voter's place.
        }
    }

    fun onReminderDisabled() {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(false)
            reminderScheduler.cancelAll()
        }
    }

    fun onSecureScreensChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSecureScreens(enabled) }
    }

    /** Called after a successful test of the device prompt. */
    fun onAppLockEnabled() {
        viewModelScope.launch { settingsRepository.setAppLockEnabled(true) }
    }

    fun onAppLockDisabled() {
        viewModelScope.launch { settingsRepository.setAppLockEnabled(false) }
    }

    fun onAppLockUnavailable() {
        transient.update { it.copy(message = SettingsMessage.LOCK_UNAVAILABLE) }
    }

    fun onConfirmationRequested(confirmation: SettingsConfirmation) {
        transient.update { it.copy(confirmation = confirmation) }
    }

    fun onConfirmationDismissed() {
        transient.update { it.copy(confirmation = null) }
    }

    /**
     * Runs the confirmed action. [clearImageCache] clears Coil's caches (an Android concern kept
     * in the UI); it runs for both actions, since photos are downloaded public data.
     */
    fun onConfirmed(clearImageCache: suspend () -> Unit) {
        val confirmation = transient.value.confirmation ?: return
        transient.update { it.copy(confirmation = null, isBusy = true) }
        viewModelScope.launch {
            when (confirmation) {
                SettingsConfirmation.CLEAR_DOWNLOADS -> {
                    electionRepository.clearCache()
                    clearImageCache()
                    transient.update { it.copy(isBusy = false, message = SettingsMessage.DOWNLOADS_CLEARED) }
                }
                SettingsConfirmation.DELETE_ALL -> {
                    reminderScheduler.cancelAll()
                    ballotRepository.deleteAll()
                    electionRepository.clearCache()
                    clearImageCache()
                    settingsRepository.clear()
                    electionSelection.select(null)
                    transient.update { it.copy(isBusy = false, dataDeleted = true) }
                }
            }
        }
    }

    fun onMessageShown() {
        transient.update { it.copy(message = null) }
    }

    fun onDataDeletedHandled() {
        transient.update { it.copy(dataDeleted = false) }
    }
}
