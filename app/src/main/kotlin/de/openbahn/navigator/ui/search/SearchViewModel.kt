package de.openbahn.navigator.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.api.DbApiBlockedException
import de.openbahn.api.DbApiException
import de.openbahn.api.DbParseException
import java.io.IOException
import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import de.openbahn.model.RatedJourney
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.domain.JourneySearchRepository
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
    val info: String? = null,
    val showPredictions: Boolean = true,
    val locale: String = "de",
    val hasSearched: Boolean = false,
)

class SearchViewModel(
    private val searchUseCase: JourneySearchRepository,
    private val trackingRepository: TrackedJourneyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var suggestJob: Job? = null

    fun setFromQuery(query: String) {
        _state.update { state ->
            val keepSelection = state.from?.name.equals(query, ignoreCase = true)
            state.copy(fromQuery = query, from = if (keepSelection) state.from else null)
        }
        loadSuggestions(query, isFrom = true)
    }

    fun setToQuery(query: String) {
        _state.update { state ->
            val keepSelection = state.to?.name.equals(query, ignoreCase = true)
            state.copy(toQuery = query, to = if (keepSelection) state.to else null)
        }
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
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, info = null, hasSearched = true) }
            val from = resolveLocation(
                _state.value.fromQuery,
                _state.value.from,
                _state.value.fromSuggestions,
            )
            val to = resolveLocation(
                _state.value.toQuery,
                _state.value.to,
                _state.value.toSuggestions,
            )
            if (from == null || to == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "error_select_stations",
                    )
                }
                return@launch
            }
            _state.update { it.copy(from = from, to = to) }
            try {
                val options = _state.value.options.copy(locale = _state.value.locale)
                if (_state.value.showPredictions) {
                    val rated = searchUseCase.searchWithPredictions(
                        from, to, options, _state.value.departureTime,
                    )
                    _state.update {
                        it.copy(
                            ratedJourneys = rated,
                            journeys = rated.map { r -> r.journey },
                            isLoading = false,
                            info = if (rated.isEmpty()) "info_no_connections" else null,
                        )
                    }
                } else {
                    val journeys = searchUseCase.searchJourneys(
                        from, to, options, _state.value.departureTime,
                    )
                    _state.update {
                        it.copy(
                            journeys = journeys,
                            ratedJourneys = emptyList(),
                            isLoading = false,
                            info = if (journeys.isEmpty()) "info_no_connections" else null,
                        )
                    }
                }
            } catch (_: DbApiBlockedException) {
                _state.update { it.copy(isLoading = false, error = "error_api_blocked") }
            } catch (_: DbParseException) {
                _state.update { it.copy(isLoading = false, error = "error_parse") }
            } catch (_: DbApiException) {
                _state.update { it.copy(isLoading = false, error = "error_search_failed") }
            } catch (_: IOException) {
                _state.update { it.copy(isLoading = false, error = "error_network") }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, error = "error_search_failed") }
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

    private suspend fun resolveLocation(
        query: String,
        selected: Location?,
        suggestions: List<Location>,
    ): Location? {
        if (selected != null && selected.name.equals(query, ignoreCase = true)) return selected
        suggestions.firstOrNull { it.name.equals(query, ignoreCase = true) }?.let { return it }
        if (query.length < 2) return null
        val results = searchUseCase.searchLocations(query, _state.value.locale)
        return results.firstOrNull { it.name.equals(query, ignoreCase = true) }
            ?: results.singleOrNull()
    }

    private fun loadSuggestions(query: String, isFrom: Boolean) {
        suggestJob?.cancel()
        if (query.length < 2) {
            _state.update {
                if (isFrom) it.copy(fromSuggestions = emptyList()) else it.copy(toSuggestions = emptyList())
            }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            try {
                val results = searchUseCase.searchLocations(query, _state.value.locale)
                _state.update {
                    if (isFrom) it.copy(fromSuggestions = results) else it.copy(toSuggestions = results)
                }
            } catch (_: Exception) {
            }
        }
    }
}
