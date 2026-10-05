package com.veronezzi.colaeleitoral.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.domain.repository.BallotRepository
import com.veronezzi.colaeleitoral.domain.repository.ElectionRepository
import com.veronezzi.colaeleitoral.domain.repository.ReminderScheduler
import com.veronezzi.colaeleitoral.domain.repository.SettingsRepository
import com.veronezzi.colaeleitoral.ui.common.AppClock
import com.veronezzi.colaeleitoral.ui.common.ElectionSelection
import com.veronezzi.colaeleitoral.ui.common.resolveElection
import com.veronezzi.colaeleitoral.ui.common.tryLocalWrite
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

enum class SettingsMessage {
    DOWNLOADS_CLEARED,
    LOCK_UNAVAILABLE,

    /** A preference could not be written (disk full): the switch keeps its previous value. */
    SAVE_FAILED,

    /** "Apagar meus dados" could not finish every step; running it again completes it. */
    DELETE_FAILED,
}

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
 * ViewModel records the outcome. Writes that fail (disk full) are reported and never crash;
 * "Apagar meus dados" deletes the picks first and the network-dependent caches last, and tries
 * every step even when one fails.
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
            if (!saved(settingsRepository.setReminderEnabled(true))) return@launch
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
            // Cancel even if the preference can't be written: the reminder must stop.
            tryLocalWrite { reminderScheduler.cancelAll() }
            saved(settingsRepository.setReminderEnabled(false))
        }
    }

    fun onSecureScreensChange(enabled: Boolean) {
        viewModelScope.launch { saved(settingsRepository.setSecureScreens(enabled)) }
    }

    /** Called after a successful test of the device prompt. */
    fun onAppLockEnabled() {
        viewModelScope.launch { saved(settingsRepository.setAppLockEnabled(true)) }
    }

    fun onAppLockDisabled() {
        viewModelScope.launch { saved(settingsRepository.setAppLockEnabled(false)) }
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
            try {
                when (confirmation) {
                    SettingsConfirmation.CLEAR_DOWNLOADS -> {
                        val cacheCleared = tryLocalWrite { electionRepository.clearCache() } != null
                        val imagesCleared = tryLocalWrite { clearImageCache() } != null
                        val message =
                            if (cacheCleared && imagesCleared) SettingsMessage.DOWNLOADS_CLEARED else SettingsMessage.DELETE_FAILED
                        transient.update { it.copy(message = message) }
                    }
                    SettingsConfirmation.DELETE_ALL -> {
                        // Most sensitive first: a later step that fails (or waits for the network)
                        // never leaves the picks behind. Every step runs even if one fails.
                        val results = listOf(
                            tryLocalWrite { ballotRepository.deleteAll() } != null,
                            tryLocalWrite { reminderScheduler.cancelAll() } != null,
                            settingsRepository.clear() is AppResult.Success,
                            tryLocalWrite { electionRepository.clearCache() } != null,
                            tryLocalWrite { clearImageCache() } != null,
                        )
                        electionSelection.select(null)
                        transient.update {
                            if (results.all { ok -> ok }) it.copy(dataDeleted = true) else it.copy(message = SettingsMessage.DELETE_FAILED)
                        }
                    }
                }
            } finally {
                transient.update { it.copy(isBusy = false) }
            }
        }
    }

    fun onMessageShown() {
        transient.update { it.copy(message = null) }
    }

    fun onDataDeletedHandled() {
        transient.update { it.copy(dataDeleted = false) }
    }

    /** True when [result] succeeded; otherwise shows "não foi salva" and returns false. */
    private fun saved(result: AppResult<Unit>): Boolean {
        if (result is AppResult.Failure) transient.update { it.copy(message = SettingsMessage.SAVE_FAILED) }
        return result is AppResult.Success
    }
}
