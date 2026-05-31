package de.openbahn.navigator.ui.tracking

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.navigator.data.TrackedJourneyWithJourney
import de.openbahn.navigator.tracking.BatteryOptimizationHelper
import de.openbahn.navigator.tracking.JourneyTrackingCoordinator
import de.openbahn.navigator.tracking.TrackedJourneyRefreshUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackingViewModel(
    private val repository: TrackedJourneyRepository,
    private val refreshUseCase: TrackedJourneyRefreshUseCase,
    private val trackingCoordinator: JourneyTrackingCoordinator,
    private val userPreferences: UserPreferencesRepository,
    private val appContext: Context,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            trackingCoordinator.restoreOnLaunch()
            refreshNow()
        }
    }

    val tracked = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<TrackedJourneyWithJourney>())

    val showBatteryOptimizationBanner = combine(
        tracked,
        userPreferences.batteryOptimizationPromptDismissed,
    ) { journeys, dismissed ->
        journeys.isNotEmpty() &&
            !dismissed &&
            !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(appContext)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun refreshNow(force: Boolean = false) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (!force && now - lastRefreshEpochMs < MIN_REFRESH_INTERVAL_MS) return@launch
            _isRefreshing.value = true
            try {
                repository.pruneArrived()
                refreshUseCase.refreshAllActive()
                lastRefreshEpochMs = System.currentTimeMillis()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun stopTracking(id: String) {
        viewModelScope.launch { repository.stopTracking(id) }
    }

    fun dismissBatteryOptimizationPrompt() {
        viewModelScope.launch {
            userPreferences.setBatteryOptimizationPromptDismissed(true)
        }
    }

    companion object {
        private const val MIN_REFRESH_INTERVAL_MS = 60_000L
        private var lastRefreshEpochMs = 0L
    }
}
