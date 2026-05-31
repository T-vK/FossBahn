package de.openbahn.navigator.ui.tracking

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.data.TrackedJourneyWithJourney
import de.openbahn.navigator.tracking.DelayTrackingWorker
import de.openbahn.navigator.tracking.TrackedJourneyRefreshUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackingViewModel(
    private val repository: TrackedJourneyRepository,
    private val refreshUseCase: TrackedJourneyRefreshUseCase,
    private val appContext: Context,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            repository.pruneArrived()
            ensureBackgroundTracking()
            refreshNow()
        }
    }

    val tracked = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<TrackedJourneyWithJourney>())

    fun refreshNow() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.pruneArrived()
                refreshUseCase.refreshAllActive()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun ensureBackgroundTracking() {
        viewModelScope.launch {
            if (repository.getActiveForWorker().isNotEmpty()) {
                DelayTrackingWorker.schedule(appContext)
            }
        }
    }

    fun stopTracking(id: String) {
        viewModelScope.launch { repository.stopTracking(id) }
    }

    fun enableBackgroundTracking(context: android.content.Context) {
        DelayTrackingWorker.schedule(context)
    }
}
