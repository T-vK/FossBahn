package de.openbahn.navigator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.model.OnTimeToleranceSettings
import de.openbahn.navigator.locale.AppLanguage
import de.openbahn.navigator.locale.AppLocaleManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPreferences: UserPreferencesRepository,
) : ViewModel() {

    val appLanguage: StateFlow<AppLanguage> = userPreferences.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.SYSTEM)

    val onTimeTolerance: StateFlow<OnTimeToleranceSettings> = userPreferences.onTimeTolerance
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            OnTimeToleranceSettings(),
        )

    val punctualityToleranceMinutes: StateFlow<Int> = onTimeTolerance
        .map { it.arrivalMinutes }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            OnTimeToleranceSettings.DEFAULT_MINUTES,
        )

    val deutschlandTicketConnectionsOnly: StateFlow<Boolean> =
        userPreferences.deutschlandTicketConnectionsOnly
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val delayNotificationIncrementMinutes: StateFlow<Int> =
        userPreferences.delayNotificationIncrementMinutes
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                de.openbahn.navigator.tracking.DelayNotificationPolicy.DEFAULT_INCREMENT_MINUTES,
            )

    val nearDepartureCheckIntervalSeconds: StateFlow<Int> =
        userPreferences.nearDepartureCheckIntervalSeconds
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                UserPreferencesRepository.DEFAULT_NEAR_DEPARTURE_CHECK_SECONDS,
            )

    val autoUpdateEnabled: StateFlow<Boolean> = userPreferences.autoUpdateEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAppLanguage(language: AppLanguage, onApplied: () -> Unit = {}) {
        viewModelScope.launch {
            userPreferences.setAppLanguage(language)
            AppLocaleManager.apply(language)
            onApplied()
        }
    }

    fun setOnTimeTolerance(settings: OnTimeToleranceSettings) {
        viewModelScope.launch {
            userPreferences.setOnTimeTolerance(settings)
        }
    }

    fun setPunctualityToleranceMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferences.setPunctualityToleranceMinutes(minutes)
        }
    }

    fun setDeutschlandTicketConnectionsOnly(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setDeutschlandTicketConnectionsOnly(enabled)
        }
    }

    fun setDelayNotificationIncrementMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferences.setDelayNotificationIncrementMinutes(minutes)
        }
    }

    fun setNearDepartureCheckIntervalSeconds(seconds: Int) {
        viewModelScope.launch {
            userPreferences.setNearDepartureCheckIntervalSeconds(seconds)
        }
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setAutoUpdateEnabled(enabled)
        }
    }
}
