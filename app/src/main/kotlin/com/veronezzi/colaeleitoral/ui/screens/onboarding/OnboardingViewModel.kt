package com.veronezzi.colaeleitoral.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veronezzi.colaeleitoral.domain.model.AppError
import com.veronezzi.colaeleitoral.domain.model.AppResult
import com.veronezzi.colaeleitoral.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Version of the independence notice. Bump it when the wording changes in a way users must see
 * again: [com.veronezzi.colaeleitoral.domain.model.UserSettings.acceptedDisclaimerVersion] below it
 * sends the user back to the notice on the next start.
 */
const val DISCLAIMER_VERSION = 1

enum class OnboardingNext { LOCATION, HOME }

data class OnboardingUiState(
    val isSaving: Boolean = false,
    /** The acceptance could not be saved (disk full): the notice stays and can be accepted again. */
    val error: AppError? = null,
    /** Set once the notice is accepted; the screen navigates and calls [OnboardingViewModel.onNavigated]. */
    val next: OnboardingNext? = null,
)

/** First-run notice ("não somos o TSE nem órgão do governo"), shown before any network access. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val state = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = state.asStateFlow()

    fun onAccept() {
        if (state.value.isSaving) return
        state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = settingsRepository.completeOnboarding(DISCLAIMER_VERSION)
            if (result is AppResult.Failure) {
                state.update { it.copy(isSaving = false, error = result.error) }
                return@launch
            }
            val hasLocation = settingsRepository.settings.first().location != null
            state.update {
                it.copy(isSaving = false, next = if (hasLocation) OnboardingNext.HOME else OnboardingNext.LOCATION)
            }
        }
    }

    fun onNavigated() {
        state.update { it.copy(next = null) }
    }
}
