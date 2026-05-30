package de.openbahn.navigator.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.api.DbApiBlockedException
import de.openbahn.api.DbApiException
import de.openbahn.api.DbParseException
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import de.openbahn.model.RatedJourney
import de.openbahn.navigator.data.FavoriteRoute
import de.openbahn.navigator.data.FavoriteRouteRepository
import de.openbahn.navigator.data.LocationHistoryRepository
import de.openbahn.navigator.data.PendingSearchRepository
import de.openbahn.navigator.data.TrackedJourneyRepository
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.navigator.domain.JourneySearchRepository
import de.openbahn.navigator.tracking.DelayTrackingWorker
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val fromQuery: String = "",
    val toQuery: String = "",
    val from: Location? = null,
    val to: Location? = null,
    val fromSuggestions: List<Location> = emptyList(),
    val toSuggestions: List<Location> = emptyList(),
    val cachedRecent: List<Location> = emptyList(),
    val favoriteLocationKeys: Set<String> = emptySet(),
    val options: JourneySearchOptions = JourneySearchOptions(),
    val departureTime: LocalDateTime = LocalDateTime.now(),
    val journeys: List<Journey> = emptyList(),
    val ratedJourneys: List<RatedJourney> = emptyList(),
    val pagingEarlier: String? = null,
    val pagingLater: String? = null,
    val isLoading: Boolean = false,
    val isLoadingEarlier: Boolean = false,
    val isLoadingLater: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val showPredictions: Boolean = true,
    val locale: String = "de",
    val hasSearched: Boolean = false,
    val showOnboarding: Boolean = false,
)

class SearchViewModel(
    private val searchUseCase: JourneySearchRepository,
    private val trackingRepository: TrackedJourneyRepository,
    private val locationHistory: LocationHistoryRepository,
    private val userPreferences: UserPreferencesRepository,
    private val favoriteRoutes: FavoriteRouteRepository,
    private val pendingSearch: PendingSearchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            userPreferences.onboardingCompleted.collect { done ->
                _state.update { it.copy(showOnboarding = !done) }
            }
        }
        viewModelScope.launch {
            locationHistory.observeRecent().collect { recent ->
                _state.update { it.copy(cachedRecent = recent) }
                refreshInstantSuggestions()
            }
        }
        viewModelScope.launch {
            locationHistory.observeFavoriteLocations().collect { favorites ->
                _state.update {
                    it.copy(favoriteLocationKeys = favorites.map { loc -> loc.evaNumber ?: loc.id }.toSet())
                }
            }
        }
        viewModelScope.launch {
            pendingSearch.pendingRoute.collect { route ->
                if (route != null) {
                    pendingSearch.consume()
                    applyFavoriteRoute(route)
                }
            }
        }
    }

    fun completeOnboarding(deutschlandTicketOnly: Boolean) {
        viewModelScope.launch {
            userPreferences.completeOnboarding(deutschlandTicketOnly)
            if (deutschlandTicketOnly) {
                _state.update {
                    it.copy(
                        options = it.options.copy(deutschlandTicketConnectionsOnly = true),
                        showOnboarding = false,
                    )
                }
            } else {
                _state.update { it.copy(showOnboarding = false) }
            }
        }
    }

    fun dismissOnboarding() {
        viewModelScope.launch {
            userPreferences.completeOnboarding(deutschlandTicketOnlyDefault = false)
            _state.update { it.copy(showOnboarding = false) }
        }
    }

    fun setFromQuery(query: String) {
        _state.update { state ->
            val keepSelection = state.from?.name.equals(query, ignoreCase = true)
            state.copy(
                fromQuery = query,
                from = if (keepSelection) state.from else null,
                fromSuggestions = instantSuggestions(query, isFrom = true),
            )
        }
        loadSuggestions(query, isFrom = true)
    }

    fun setToQuery(query: String) {
        _state.update { state ->
            val keepSelection = state.to?.name.equals(query, ignoreCase = true)
            state.copy(
                toQuery = query,
                to = if (keepSelection) state.to else null,
                toSuggestions = instantSuggestions(query, isFrom = false),
            )
        }
        loadSuggestions(query, isFrom = false)
    }

    fun selectFrom(location: Location) {
        viewModelScope.launch {
            locationHistory.recordUsed(location)
            _state.update { it.copy(from = location, fromQuery = location.name, fromSuggestions = emptyList()) }
        }
    }

    fun selectTo(location: Location) {
        viewModelScope.launch {
            locationHistory.recordUsed(location)
            _state.update { it.copy(to = location, toQuery = location.name, toSuggestions = emptyList()) }
        }
    }

    fun updateOptions(options: JourneySearchOptions) {
        _state.update { it.copy(options = options) }
    }

    fun setWhen(time: LocalDateTime, arrivalSearch: Boolean) {
        _state.update { it.copy(departureTime = time, options = it.options.copy(arrivalSearch = arrivalSearch)) }
    }

    fun setDepartureTime(time: LocalDateTime) {
        _state.update { it.copy(departureTime = time) }
    }

    fun setLocale(locale: String) {
        _state.update { it.copy(locale = locale, options = _state.value.options.copy(locale = locale)) }
    }

    fun clearRecentLocations() {
        viewModelScope.launch {
            locationHistory.clearRecent()
            refreshInstantSuggestions()
        }
    }

    fun toggleFavoriteLocation(location: Location) {
        viewModelScope.launch {
            if (locationHistory.isFavoriteLocation(location)) {
                locationHistory.removeFavoriteLocation(location)
            } else {
                locationHistory.addFavoriteLocation(location)
            }
        }
    }

    fun saveCurrentRouteAsFavorite(label: String? = null) {
        val from = _state.value.from ?: return
        val to = _state.value.to ?: return
        viewModelScope.launch {
            favoriteRoutes.save(from, to, _state.value.options, label)
        }
    }

    fun applyFavoriteRoute(route: FavoriteRoute) {
        _state.update {
            it.copy(
                from = route.from,
                to = route.to,
                fromQuery = route.from.name,
                toQuery = route.to.name,
                options = route.options,
                departureTime = LocalDateTime.now(),
            )
        }
        search()
    }

    fun search() {
        viewModelScope.launch { performSearch(replaceResults = true) }
    }

    fun loadEarlierConnections() {
        val token = _state.value.pagingEarlier ?: return
        viewModelScope.launch { loadMore(pagingReference = token, earlier = true) }
    }

    fun loadLaterConnections() {
        val token = _state.value.pagingLater ?: return
        viewModelScope.launch { loadMore(pagingReference = token, earlier = false) }
    }

    fun trackJourney(journey: Journey, context: android.content.Context) {
        val from = _state.value.from?.name ?: return
        val to = _state.value.to?.name ?: return
        viewModelScope.launch {
            trackingRepository.track(journey, from, to)
            DelayTrackingWorker.schedule(context)
        }
    }

    private fun instantSuggestions(query: String, isFrom: Boolean): List<Location> {
        val recent = _state.value.cachedRecent
        val q = query.trim()
        val recentMatches = if (q.length < 1) {
            recent.take(8)
        } else {
            recent.filter { it.name.contains(q, ignoreCase = true) }.take(8)
        }
        return if (isFrom) recentMatches else recentMatches
    }

    private fun refreshInstantSuggestions() {
        _state.update { state ->
            state.copy(
                fromSuggestions = if (state.fromSuggestions.isNotEmpty() || state.fromQuery.length >= 1) {
                    mergeSuggestions(state.fromQuery, state.fromSuggestions)
                } else {
                    instantSuggestions(state.fromQuery, isFrom = true)
                },
                toSuggestions = if (state.toSuggestions.isNotEmpty() || state.toQuery.length >= 1) {
                    mergeSuggestions(state.toQuery, state.toSuggestions)
                } else {
                    instantSuggestions(state.toQuery, isFrom = false)
                },
            )
        }
    }

    private suspend fun performSearch(replaceResults: Boolean) {
        OpenBahnDebugLog.d(
            "Search",
            "search() fromQuery=\"${_state.value.fromQuery}\" toQuery=\"${_state.value.toQuery}\" " +
                "departureTime=${_state.value.departureTime} arrivalSearch=${_state.value.options.arrivalSearch}",
        )
        _state.update {
            it.copy(
                isLoading = true,
                isLoadingEarlier = false,
                isLoadingLater = false,
                error = null,
                info = null,
                hasSearched = true,
                journeys = if (replaceResults) emptyList() else it.journeys,
                ratedJourneys = if (replaceResults) emptyList() else it.ratedJourneys,
                pagingEarlier = if (replaceResults) null else it.pagingEarlier,
                pagingLater = if (replaceResults) null else it.pagingLater,
            )
        }
        val from = resolveLocation("from", _state.value.fromQuery, _state.value.from, _state.value.fromSuggestions)
        val to = resolveLocation("to", _state.value.toQuery, _state.value.to, _state.value.toSuggestions)
        if (from == null || to == null) {
            OpenBahnDebugLog.w("Search", "search() aborted: could not resolve stations")
            _state.update { it.copy(isLoading = false, error = "error_select_stations") }
            return
        }
        _state.update { it.copy(from = from, to = to) }
        viewModelScope.launch {
            locationHistory.recordUsed(from)
            locationHistory.recordUsed(to)
        }
        runSearch(from, to, pagingReference = null, replaceResults = replaceResults)
    }

    private suspend fun loadMore(pagingReference: String, earlier: Boolean) {
        val from = _state.value.from ?: return
        val to = _state.value.to ?: return
        _state.update {
            it.copy(
                isLoadingEarlier = earlier,
                isLoadingLater = !earlier,
                error = null,
            )
        }
        runSearch(from, to, pagingReference = pagingReference, replaceResults = false, prepend = earlier)
    }

    private suspend fun runSearch(
        from: Location,
        to: Location,
        pagingReference: String?,
        replaceResults: Boolean,
        prepend: Boolean = false,
    ) {
        try {
            val options = _state.value.options.copy(locale = _state.value.locale)
            val page = searchUseCase.searchJourneys(
                from, to, options, _state.value.departureTime, pagingReference,
            )
            val existing = if (replaceResults) emptyList() else _state.value.journeys
            val existingIds = existing.map { it.id }.toSet()
            val mergedJourneys = mergeJourneys(existing, page.journeys, prepend)
            val rated = if (_state.value.showPredictions) {
                val newOnes = page.journeys.filter { it.id !in existingIds }
                val newRated = if (newOnes.isNotEmpty()) searchUseCase.rateJourneys(newOnes) else emptyList()
                mergeRated(
                    existing = if (replaceResults) emptyList() else _state.value.ratedJourneys,
                    incoming = newRated,
                    prepend = prepend,
                )
            } else {
                emptyList()
            }
            logSearchOutcome(mergedJourneys.size, mergedJourneys.isEmpty() && replaceResults)
            _state.update {
                it.copy(
                    journeys = mergedJourneys,
                    ratedJourneys = rated,
                    pagingEarlier = when {
                        replaceResults -> page.pagingEarlier
                        prepend -> page.pagingEarlier
                        else -> it.pagingEarlier
                    },
                    pagingLater = when {
                        replaceResults -> page.pagingLater
                        !prepend && pagingReference != null -> page.pagingLater
                        else -> it.pagingLater
                    },
                    isLoading = false,
                    isLoadingEarlier = false,
                    isLoadingLater = false,
                    info = if (mergedJourneys.isEmpty() && replaceResults) "info_no_connections" else null,
                )
            }
        } catch (e: DbApiBlockedException) {
            _state.update {
                it.copy(isLoading = false, isLoadingEarlier = false, isLoadingLater = false, error = "error_api_blocked")
            }
        } catch (e: DbParseException) {
            _state.update {
                it.copy(isLoading = false, isLoadingEarlier = false, isLoadingLater = false, error = "error_parse")
            }
        } catch (e: DbApiException) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isLoadingEarlier = false,
                    isLoadingLater = false,
                    error = "error_search_failed",
                )
            }
        } catch (e: IOException) {
            _state.update {
                it.copy(isLoading = false, isLoadingEarlier = false, isLoadingLater = false, error = "error_network")
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isLoadingEarlier = false,
                    isLoadingLater = false,
                    error = "error_search_failed",
                )
            }
        }
    }

    private fun mergeJourneys(
        existing: List<Journey>,
        incoming: List<Journey>,
        prepend: Boolean,
    ): List<Journey> {
        val combined = if (prepend) incoming + existing else existing + incoming
        return combined.distinctBy { it.id }
    }

    private fun mergeRated(
        existing: List<RatedJourney>,
        incoming: List<RatedJourney>,
        prepend: Boolean,
    ): List<RatedJourney> {
        val combined = if (prepend) incoming + existing else existing + incoming
        return combined.distinctBy { it.journey.id }
    }

    private fun logSearchOutcome(journeyCount: Int, showNoConnections: Boolean) {
        if (showNoConnections) {
            OpenBahnDebugLog.w("Search", "search() UI will show info_no_connections — $journeyCount journey(s)")
        } else {
            OpenBahnDebugLog.d("Search", "search() success: $journeyCount journey(s) for UI")
        }
    }

    private suspend fun resolveLocation(
        label: String,
        query: String,
        selected: Location?,
        suggestions: List<Location>,
    ): Location? {
        if (selected != null && selected.name.equals(query, ignoreCase = true)) {
            return selected
        }
        suggestions.firstOrNull { it.name.equals(query, ignoreCase = true) }?.let { return it }
        if (query.length < 2) return null
        val recent = locationHistory.recentMatching(query)
        recent.firstOrNull { it.name.equals(query, ignoreCase = true) }?.let { return it }
        val results = searchUseCase.searchLocations(query, _state.value.locale)
        results.firstOrNull { it.name.equals(query, ignoreCase = true) }?.let { return it }
        return results.singleOrNull()
    }

    private fun loadSuggestions(query: String, isFrom: Boolean) {
        suggestJob?.cancel()
        val instant = mergeSuggestions(query, instantSuggestions(query, isFrom))
        _state.update {
            if (isFrom) it.copy(fromSuggestions = instant) else it.copy(toSuggestions = instant)
        }
        if (query.length < 2) {
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            try {
                val recent = locationHistory.recentMatching(query)
                val api = searchUseCase.searchLocations(query, _state.value.locale)
                val merged = mergeSuggestions(query, recent + api)
                _state.update {
                    if (isFrom) it.copy(fromSuggestions = merged) else it.copy(toSuggestions = merged)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun mergeSuggestions(query: String, suggestions: List<Location>): List<Location> {
        val q = query.trim()
        val recent = _state.value.cachedRecent.filter {
            q.isEmpty() || it.name.contains(q, ignoreCase = true)
        }
        val combined = (recent + suggestions).distinctBy { it.evaNumber ?: it.id }
        return combined.take(8)
    }
}
