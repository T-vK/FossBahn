package de.openbahn.navigator.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import de.openbahn.model.RatedJourney
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.domain.JourneySearchUseCase
import de.openbahn.navigator.tracking.DelayTrackingWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class SearchUiState(
    val fromQuery: String = "",
    val toQuery: String = "",
    val from: Location? = null,
    val to: Location? = null,
    val fromSuggestions: List<Location> = emptyList(),
    val toSuggestions: List<Location> = emptyList(),
    val options: JourneySearchOptions = JourneySearchOptions(),
    val departureTime: LocalDateTime = LocalDateTime.now(),
    val journeys: List<Journey> = emptyList(),
    val ratedJourneys: List<RatedJourney> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showPredictions: Boolean = true,
    val locale: String = "de",
)

class SearchViewModel(
    private val searchUseCase: JourneySearchUseCase,
    private val trackingRepository: TrackedJourneyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var suggestJob: Job? = null

    fun setFromQuery(query: String) {
        _state.update { it.copy(fromQuery = query) }
        loadSuggestions(query, isFrom = true)
    }

    fun setToQuery(query: String) {
        _state.update { it.copy(toQuery = query) }
        loadSuggestions(query, isFrom = false)
    }

    fun selectFrom(location: Location) {
        _state.update { it.copy(from = location, fromQuery = location.name, fromSuggestions = emptyList()) }
    }

    fun selectTo(location: Location) {
        _state.update { it.copy(to = location, toQuery = location.name, toSuggestions = emptyList()) }
    }

    fun updateOptions(options: JourneySearchOptions) {
        _state.update { it.copy(options = options) }
    }

    fun setDepartureTime(time: LocalDateTime) {
        _state.update { it.copy(departureTime = time) }
    }

    fun setLocale(locale: String) {
        _state.update { it.copy(locale = locale, options = _state.value.options.copy(locale = locale)) }
    }

    fun search() {
        val from = _state.value.from
        val to = _state.value.to
        if (from == null || to == null) {
            _state.update { it.copy(error = "Select origin and destination") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val options = _state.value.options.copy(locale = _state.value.locale)
                if (_state.value.showPredictions) {
                    val rated = searchUseCase.searchWithPredictions(
                        from, to, options, _state.value.departureTime,
                    )
                    _state.update { it.copy(ratedJourneys = rated, journeys = rated.map { r -> r.journey }, isLoading = false) }
                } else {
                    val journeys = searchUseCase.searchJourneys(
                        from, to, options, _state.value.departureTime,
                    )
                    _state.update { it.copy(journeys = journeys, ratedJourneys = emptyList(), isLoading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Search failed") }
            }
        }
    }

    fun trackJourney(journey: Journey, context: android.content.Context) {
        val from = _state.value.from?.name ?: return
        val to = _state.value.to?.name ?: return
        viewModelScope.launch {
            trackingRepository.track(journey, from, to)
            DelayTrackingWorker.schedule(context)
        }
    }

    private fun loadSuggestions(query: String, isFrom: Boolean) {
        suggestJob?.cancel()
        if (query.length < 2) return
        suggestJob = viewModelScope.launch {
            delay(300)
            try {
                val results = searchUseCase.searchLocations(query, _state.value.locale)
                _state.update {
                    if (isFrom) it.copy(fromSuggestions = results)
                    else it.copy(toSuggestions = results)
                }
            } catch (_: Exception) {
            }
        }
    }
}
