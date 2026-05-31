package de.openbahn.navigator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.navigator.locale.AppLanguage
import de.openbahn.navigator.locale.AppLocaleManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPreferences: UserPreferencesRepository,
) : ViewModel() {

    val appLanguage: StateFlow<AppLanguage> = userPreferences.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.SYSTEM)

    val punctualityToleranceMinutes: StateFlow<Int> = userPreferences.punctualityToleranceMinutes
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            de.openbahn.api.JourneyRatingOptions.DEFAULT_PUNCTUALITY_TOLERANCE_MINUTES,
        )

    fun setAppLanguage(language: AppLanguage, onApplied: () -> Unit = {}) {
        viewModelScope.launch {
            userPreferences.setAppLanguage(language)
            AppLocaleManager.apply(language)
            onApplied()
        }
    }

    fun setPunctualityToleranceMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferences.setPunctualityToleranceMinutes(minutes)
        }
    }
}
