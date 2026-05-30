package de.openbahn.navigator.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.data.TrackedJourneyWithJourney
import de.openbahn.navigator.tracking.DelayTrackingWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackingViewModel(
    private val repository: TrackedJourneyRepository,
) : ViewModel() {
    init {
        viewModelScope.launch { repository.pruneArrived() }
    }

    val tracked = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<TrackedJourneyWithJourney>())

    fun stopTracking(id: String) {
        viewModelScope.launch { repository.stopTracking(id) }
    }

    fun enableBackgroundTracking(context: android.content.Context) {
        DelayTrackingWorker.schedule(context)
    }
}
