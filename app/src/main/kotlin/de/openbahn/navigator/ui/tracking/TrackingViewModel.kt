package de.openbahn.navigator.ui.tracking

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.model.Location
import de.openbahn.navigator.data.AlternativesSearchRequest
import de.openbahn.navigator.data.PendingSearchRepository
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.navigator.data.TrackedJourneyWithJourney
import de.openbahn.navigator.domain.JourneySearchRepository
import de.openbahn.navigator.tracking.BatteryOptimizationHelper
import de.openbahn.navigator.tracking.JourneyTrackingCoordinator
import de.openbahn.navigator.tracking.TrackedJourneyRefreshUseCase
import de.openbahn.navigator.ui.util.parseJourneyDateTime
import java.time.LocalDateTime
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
    private val searchRepository: JourneySearchRepository,
    private val pendingSearch: PendingSearchRepository,
    private val trackingCoordinator: JourneyTrackingCoordinator,
    private val userPreferences: UserPreferencesRepository,
    private val appContext: Context,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _highlightTrackedJourneyId = MutableStateFlow<String?>(null)
    val highlightTrackedJourneyId: StateFlow<String?> = _highlightTrackedJourneyId.asStateFlow()

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

    fun applyNotificationHighlight(journeyId: String) {
        _highlightTrackedJourneyId.value = journeyId
    }

    fun clearHighlight() {
        _highlightTrackedJourneyId.value = null
    }

    fun openAlternatives(trackedId: String, onNavigateToSearch: () -> Unit) {
        viewModelScope.launch {
            val item = repository.findActiveWithJourney(trackedId) ?: return@launch
            val from = repository.decodeLocation(item.entity.fromLocationJson)
                ?: resolveByName(item.entity.fromName)
                ?: return@launch
            val to = repository.decodeLocation(item.entity.toLocationJson)
                ?: resolveByName(item.entity.toName)
                ?: return@launch
            val departure = repository.departureDateTime(item.entity)
                ?: parseJourneyDateTime(item.journey.departure)
                ?: LocalDateTime.now()
            pendingSearch.scheduleAlternatives(
                AlternativesSearchRequest(
                    from = from,
                    to = to,
                    departureTime = departure,
                    arrivalSearch = false,
                ),
            )
            onNavigateToSearch()
        }
    }

    private suspend fun resolveByName(name: String): Location? {
        val locale = "de"
        return searchRepository.searchLocations(name, locale)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: searchRepository.searchLocations(name, locale).firstOrNull()
    }

    companion object {
        private const val MIN_REFRESH_INTERVAL_MS = 60_000L
        private var lastRefreshEpochMs = 0L
    }
}
