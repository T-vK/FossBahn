package de.openbahn.navigator.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.api.DbApiBlockedException
import de.openbahn.api.JourneyRatingOptions
import de.openbahn.api.DbApiException
import de.openbahn.api.DbParseException
import de.openbahn.api.JourneySearchTime
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.api.haltIdForJourney
import de.openbahn.model.Journey
import de.openbahn.model.JourneySearchResult
import de.openbahn.model.JourneySearchOptions
import de.openbahn.model.Location
import de.openbahn.model.OnTimeToleranceSettings
import de.openbahn.model.RatedJourney
import de.openbahn.model.TransportProduct
import de.openbahn.model.ViaStop
import de.openbahn.navigator.data.AlternativesSearchRequest
import de.openbahn.navigator.location.DeviceLocationProvider
import de.openbahn.navigator.data.FavoriteRoute
import de.openbahn.navigator.data.FavoriteRouteRepository
import de.openbahn.navigator.data.LocationHistoryRepository
import de.openbahn.navigator.data.autocompleteMatchRank
import de.openbahn.navigator.data.matchesAutocompleteQuery
import de.openbahn.navigator.data.stableKey
import de.openbahn.navigator.data.PendingSearchRepository
import de.openbahn.navigator.data.TrackedJourneyRepository
import android.content.Context
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.navigator.locale.AppLanguage
import de.openbahn.navigator.domain.JourneySearchRepository
import de.openbahn.navigator.tracking.DelayTrackingWorker
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveLocationField {
    NONE,
    FROM,
    TO,
}

data class SearchUiState(
    val fromQuery: String = "",
    val toQuery: String = "",
    val from: Location? = null,
    val to: Location? = null,
    val activeLocationField: ActiveLocationField = ActiveLocationField.NONE,
    val fromSuggestions: List<Location> = emptyList(),
    val toSuggestions: List<Location> = emptyList(),
    val cachedRecent: List<Location> = emptyList(),
    val favoriteLocationKeys: Set<String> = emptySet(),
    val options: JourneySearchOptions = JourneySearchOptions(),
    val departureTime: LocalDateTime = JourneySearchTime.nowBerlin(),
    val journeys: List<Journey> = emptyList(),
    val ratedJourneys: List<RatedJourney> = emptyList(),
    val pagingEarlier: String? = null,
    val pagingLater: String? = null,
    /** Arrival-search connections hidden until the user taps "Earlier connections". */
    val hiddenArrivalJourneys: List<Journey> = emptyList(),
    val isLoading: Boolean = false,
    /** True while adjacent arrival-search pages are fetched after the first page is shown. */
    val isRefiningArrivalResults: Boolean = false,
    val isLoadingEarlier: Boolean = false,
    val isLoadingLater: Boolean = false,
    /** True while Bahn-Vorhersage ratings are still loading after connections are shown. */
    val predictionsLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val showPredictions: Boolean = true,
    val locale: String = "de",
    val hasSearched: Boolean = false,
    val showOnboarding: Boolean = false,
    val onTimeTolerance: OnTimeToleranceSettings = OnTimeToleranceSettings(),
    /** Incremented when a new search returns results; UI scrolls to the first connection. */
    val scrollToResultsToken: Long = 0L,
    val viaStops: List<ViaStopField> = emptyList(),
    val showViaStopsEditor: Boolean = false,
    val activeViaIndex: Int? = null,
    val viaSuggestions: List<Location> = emptyList(),
)

class SearchViewModel(
    private val searchUseCase: JourneySearchRepository,
    private val trackingRepository: TrackedJourneyRepository,
    private val locationHistory: LocationHistoryRepository,
    private val userPreferences: UserPreferencesRepository,
    private val favoriteRoutes: FavoriteRouteRepository,
    private val pendingSearch: PendingSearchRepository,
    private val deviceLocation: DeviceLocationProvider,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var suggestJob: Job? = null
    private var predictionsJob: Job? = null
    private var searchGeneration: Long = 0L

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
        viewModelScope.launch {
            pendingSearch.pendingAlternatives.collect { request ->
                if (request != null) {
                    pendingSearch.consumeAlternatives()
                    applyAlternativesSearch(request)
                }
            }
        }
        viewModelScope.launch {
            userPreferences.appLanguage.collect { language ->
                val apiLocale = language.apiLocale(appContext)
                _state.update {
                    it.copy(
                        locale = apiLocale,
                        options = it.options.copy(locale = apiLocale),
                    )
                }
            }
        }
        viewModelScope.launch {
            userPreferences.onTimeTolerance.collect { tolerance ->
                _state.update { it.copy(onTimeTolerance = tolerance) }
            }
        }
        viewModelScope.launch {
            userPreferences.deutschlandTicketConnectionsOnly.collect { enabled ->
                _state.update {
                    it.copy(options = it.options.copy(deutschlandTicketConnectionsOnly = enabled))
                }
            }
        }
    }

    private fun currentRatingOptions(): JourneyRatingOptions = JourneyRatingOptions(
        minTransferMinutes = _state.value.options.minTransferMinutes,
        onTimeTolerance = _state.value.onTimeTolerance,
    )

    fun completeOnboarding(deutschlandTicketOnly: Boolean) {
        viewModelScope.launch {
            userPreferences.completeOnboarding(deutschlandTicketOnly)
            _state.update {
                it.copy(
                    options = it.options.copy(deutschlandTicketConnectionsOnly = deutschlandTicketOnly),
                    showOnboarding = false,
                )
            }
        }
    }

    fun dismissOnboarding() {
        viewModelScope.launch {
            userPreferences.completeOnboarding(deutschlandTicketOnlyDefault = false)
            _state.update { it.copy(showOnboarding = false) }
        }
    }

    fun onFromFocusChanged(focused: Boolean) {
        if (focused) {
            _state.update {
                it.copy(
                    activeLocationField = ActiveLocationField.FROM,
                    toSuggestions = emptyList(),
                )
            }
            refreshSuggestionsFor(ActiveLocationField.FROM)
        } else if (_state.value.activeLocationField == ActiveLocationField.FROM) {
            _state.update { it.copy(activeLocationField = ActiveLocationField.NONE, fromSuggestions = emptyList()) }
        }
    }

    fun onToFocusChanged(focused: Boolean) {
        if (focused) {
            _state.update {
                it.copy(
                    activeLocationField = ActiveLocationField.TO,
                    fromSuggestions = emptyList(),
                )
            }
            refreshSuggestionsFor(ActiveLocationField.TO)
        } else if (_state.value.activeLocationField == ActiveLocationField.TO) {
            _state.update { it.copy(activeLocationField = ActiveLocationField.NONE, toSuggestions = emptyList()) }
        }
    }

    fun setFromQuery(query: String) {
        _state.update { state ->
            val keepSelection = state.from?.name.equals(query, ignoreCase = true)
            state.copy(
                fromQuery = query,
                from = if (keepSelection) state.from else null,
                activeLocationField = ActiveLocationField.FROM,
                toSuggestions = emptyList(),
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
                activeLocationField = ActiveLocationField.TO,
                fromSuggestions = emptyList(),
            )
        }
        loadSuggestions(query, isFrom = false)
    }

    fun selectFrom(location: Location, displayLabel: String? = null) {
        _state.update {
            it.copy(
                from = location,
                fromQuery = displayLabel ?: location.name,
                fromSuggestions = emptyList(),
                activeLocationField = ActiveLocationField.NONE,
            )
        }
        viewModelScope.launch { locationHistory.recordUsed(location) }
    }

    fun selectTo(location: Location, displayLabel: String? = null) {
        _state.update {
            it.copy(
                to = location,
                toQuery = displayLabel ?: location.name,
                toSuggestions = emptyList(),
                activeLocationField = ActiveLocationField.NONE,
            )
        }
        viewModelScope.launch { locationHistory.recordUsed(location) }
    }

    fun updateOptions(options: JourneySearchOptions) {
        _state.update { it.copy(options = options) }
    }

    fun setDeutschlandTicketConnectionsOnly(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setDeutschlandTicketConnectionsOnly(enabled)
        }
    }

    fun setWhen(time: LocalDateTime, arrivalSearch: Boolean) {
        _state.update { it.copy(departureTime = time, options = it.options.copy(arrivalSearch = arrivalSearch)) }
    }

    fun setDepartureTime(time: LocalDateTime) {
        _state.update { it.copy(departureTime = time) }
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
                departureTime = JourneySearchTime.nowBerlin(),
            )
        }
        search()
    }

    fun search() {
        viewModelScope.launch { performSearch(replaceResults = true) }
    }

    fun loadEarlierConnections() {
        if (_state.value.options.arrivalSearch && _state.value.hiddenArrivalJourneys.isNotEmpty()) {
            viewModelScope.launch { revealHiddenArrivalJourneys() }
            return
        }
        val token = _state.value.pagingEarlier ?: return
        viewModelScope.launch { loadMore(pagingReference = token, earlier = true) }
    }

    fun loadLaterConnections() {
        val token = _state.value.pagingLater ?: return
        viewModelScope.launch { loadMore(pagingReference = token, earlier = false) }
    }

    fun trackJourney(journey: Journey) {
        val from = _state.value.from ?: return
        val to = _state.value.to ?: return
        viewModelScope.launch {
            trackingRepository.track(
                journey = journey,
                fromName = from.name,
                toName = to.name,
                fromLocation = from,
                toLocation = to,
            )
        }
    }

    fun toggleViaStopsEditor() {
        _state.update { state ->
            val show = !state.showViaStopsEditor
            state.copy(
                showViaStopsEditor = show,
                viaStops = if (show && state.viaStops.isEmpty()) listOf(ViaStopField()) else state.viaStops,
                activeViaIndex = null,
                viaSuggestions = emptyList(),
            )
        }
    }

    fun addViaStop() {
        _state.update { it.copy(viaStops = it.viaStops + ViaStopField()) }
    }

    fun removeViaStop(index: Int) {
        _state.update { state ->
            state.copy(
                viaStops = state.viaStops.filterIndexed { i, _ -> i != index },
                activeViaIndex = if (state.activeViaIndex == index) null else state.activeViaIndex,
                viaSuggestions = emptyList(),
            )
        }
    }

    fun setViaQuery(index: Int, query: String) {
        _state.update { state ->
            val stops = state.viaStops.toMutableList()
            if (index !in stops.indices) return@update state
            val current = stops[index]
            val keep = current.location?.name.equals(query, ignoreCase = true)
            stops[index] = current.copy(query = query, location = if (keep) current.location else null)
            state.copy(
                viaStops = stops,
                activeViaIndex = index,
                activeLocationField = ActiveLocationField.NONE,
                fromSuggestions = emptyList(),
                toSuggestions = emptyList(),
            )
        }
        loadViaSuggestions(index, query)
    }

    fun onViaFocusChanged(index: Int, focused: Boolean) {
        if (focused) {
            _state.update {
                it.copy(
                    activeViaIndex = index,
                    activeLocationField = ActiveLocationField.NONE,
                    fromSuggestions = emptyList(),
                    toSuggestions = emptyList(),
                )
            }
            val query = _state.value.viaStops.getOrNull(index)?.query.orEmpty()
            refreshViaSuggestions(index, query)
        } else if (_state.value.activeViaIndex == index) {
            _state.update { it.copy(activeViaIndex = null, viaSuggestions = emptyList()) }
        }
    }

    fun selectVia(index: Int, location: Location, displayLabel: String? = null) {
        _state.update { state ->
            val stops = state.viaStops.toMutableList()
            if (index !in stops.indices) return@update state
            stops[index] = stops[index].copy(location = location, query = displayLabel ?: location.name)
            state.copy(
                viaStops = stops,
                viaSuggestions = emptyList(),
                activeViaIndex = null,
            )
        }
        viewModelScope.launch { locationHistory.recordUsed(location) }
    }

    fun useCurrentLocationForFrom() {
        viewModelScope.launch { resolveCurrentLocation { loc, label -> selectFrom(loc, label) } }
    }

    fun useCurrentLocationForTo() {
        viewModelScope.launch { resolveCurrentLocation { loc, label -> selectTo(loc, label) } }
    }

    fun useCurrentLocationForVia(index: Int) {
        viewModelScope.launch {
            resolveCurrentLocation { location, label -> selectVia(index, location, label) }
        }
    }

    fun reportLocationPermissionDenied() {
        _state.update { it.copy(error = "error_location_permission") }
    }

    private suspend fun resolveCurrentLocation(onResolved: (Location, String) -> Unit) {
        if (!deviceLocation.hasLocationPermission()) {
            _state.update { it.copy(error = "error_location_permission") }
            return
        }
        val resolved = deviceLocation.resolveCurrentLocation(_state.value.locale)
        if (resolved != null) {
            onResolved(resolved.searchLocation, resolved.displayLabel)
        } else {
            _state.update { it.copy(error = "error_location_unavailable") }
        }
    }

    private fun applyAlternativesSearch(request: AlternativesSearchRequest) {
        _state.update {
            it.copy(
                from = request.from,
                to = request.to,
                fromQuery = request.from.name,
                toQuery = request.to.name,
                departureTime = request.departureTime,
                options = it.options.copy(arrivalSearch = request.arrivalSearch),
                viaStops = emptyList(),
                showViaStopsEditor = false,
            )
        }
        search()
    }

    private fun searchOptionsWithVia(): JourneySearchOptions {
        val state = _state.value
        var options = state.options.copy(
            locale = state.locale,
            viaStops = state.viaStops.mapNotNull { field ->
                field.location?.let { loc -> ViaStop(locationId = loc.haltIdForJourney()) }
            },
        )
        if (options.products.isEmpty()) {
            options = options.copy(products = TransportProduct.ALL)
        }
        return options
    }

    private fun refreshInstantSuggestions() {
        val viaIndex = _state.value.activeViaIndex
        if (viaIndex != null) {
            refreshViaSuggestions(viaIndex, _state.value.viaStops.getOrNull(viaIndex)?.query.orEmpty())
            return
        }
        when (_state.value.activeLocationField) {
            ActiveLocationField.FROM -> refreshSuggestionsFor(ActiveLocationField.FROM)
            ActiveLocationField.TO -> refreshSuggestionsFor(ActiveLocationField.TO)
            ActiveLocationField.NONE -> Unit
        }
    }

    private fun loadViaSuggestions(index: Int, query: String) {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            val local = locationHistory.rankedForAutocomplete(query)
                .excludingStations(_state.value, excludeViaIndex = index)
                .take(8)
            applyViaSuggestions(index, local)
            if (query.length < 2) return@launch
            delay(300)
            if (_state.value.activeViaIndex != index) return@launch
            val api = searchUseCase.searchLocations(query, _state.value.locale)
                .excludingStations(_state.value, excludeViaIndex = index)
            applyViaSuggestions(index, mergeWithApiResults(local, api, query))
        }
    }

    private fun refreshViaSuggestions(index: Int, query: String) {
        viewModelScope.launch {
            val ranked = locationHistory.rankedForAutocomplete(query)
                .excludingStations(_state.value, excludeViaIndex = index)
            applyViaSuggestions(index, ranked)
        }
    }

    private fun applyViaSuggestions(index: Int, suggestions: List<Location>) {
        _state.update { state ->
            if (state.activeViaIndex != index) state
            else state.copy(viaSuggestions = suggestions.take(8))
        }
    }

    private fun refreshSuggestionsFor(field: ActiveLocationField) {
        viewModelScope.launch {
            val query = when (field) {
                ActiveLocationField.FROM -> _state.value.fromQuery
                ActiveLocationField.TO -> _state.value.toQuery
                ActiveLocationField.NONE -> return@launch
            }
            val ranked = locationHistory.rankedForAutocomplete(query)
            applySuggestions(field, ranked)
        }
    }

    private suspend fun performSearch(replaceResults: Boolean) {
        OpenBahnDebugLog.d(
            "Search",
            "search() fromQuery=\"${_state.value.fromQuery}\" toQuery=\"${_state.value.toQuery}\" " +
                "departureTime=${_state.value.departureTime} arrivalSearch=${_state.value.options.arrivalSearch}",
        )
        predictionsJob?.cancel()
        searchGeneration++
        _state.update {
            it.copy(
                isLoading = true,
                isRefiningArrivalResults = false,
                isLoadingEarlier = false,
                isLoadingLater = false,
                predictionsLoading = false,
                error = null,
                info = null,
                hasSearched = true,
                activeLocationField = ActiveLocationField.NONE,
                fromSuggestions = emptyList(),
                toSuggestions = emptyList(),
                journeys = if (replaceResults) emptyList() else it.journeys,
                ratedJourneys = if (replaceResults) emptyList() else it.ratedJourneys,
                hiddenArrivalJourneys = if (replaceResults) emptyList() else it.hiddenArrivalJourneys,
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

    private suspend fun revealHiddenArrivalJourneys() {
        val hidden = _state.value.hiddenArrivalJourneys
        if (hidden.isEmpty()) return
        _state.update {
            it.copy(
                isLoadingEarlier = true,
                error = null,
            )
        }
        val generation = searchGeneration
        val merged = mergeJourneys(_state.value.journeys, hidden, prepend = true)
        _state.update {
            it.copy(
                journeys = merged,
                hiddenArrivalJourneys = emptyList(),
                isLoadingEarlier = false,
            )
        }
        if (_state.value.showPredictions) {
            startPredictionsJob(hidden, prepend = true, generation)
        }
    }

    private suspend fun runSearch(
        from: Location,
        to: Location,
        pagingReference: String?,
        replaceResults: Boolean,
        prepend: Boolean = false,
    ) {
        try {
            val options = searchOptionsWithVia()
            // Pass the user's selected time verbatim; DbVendoClient normalizes it (arrival-aware)
            // so a chosen departure/arrival time is never silently replaced with the current time.
            val whenTime = _state.value.departureTime
            val page = searchUseCase.searchJourneys(
                from, to, options, whenTime, pagingReference,
            )
            val existing = if (replaceResults) emptyList() else _state.value.journeys
            val existingIds = existing.map { it.id }.toSet()
            val generation = searchGeneration
            val isInitialArrivalSearch = options.arrivalSearch && replaceResults && pagingReference == null
            val needsArrivalRefinement = isInitialArrivalSearch && page.journeys.isNotEmpty() &&
                (page.pagingEarlier != null || page.pagingLater != null)

            if (needsArrivalRefinement) {
                publishSearchResults(
                    existing = existing,
                    existingIds = existingIds,
                    incoming = page.journeys,
                    hiddenArrivalJourneys = emptyList(),
                    page = page,
                    fetchedEarlierPagingEarlier = null,
                    fetchedLaterPagingLater = null,
                    replaceResults = replaceResults,
                    prepend = prepend,
                    options = options,
                    generation = generation,
                    isRefiningArrivalResults = true,
                )
                try {
                    refineInitialArrivalSearch(
                        from = from,
                        to = to,
                        options = options,
                        whenTime = whenTime,
                        initialPage = page,
                        generation = generation,
                    )
                } catch (e: Exception) {
                    OpenBahnDebugLog.w("Search", "arrival refinement failed: ${e.message}")
                    _state.update { it.copy(isRefiningArrivalResults = false) }
                }
                return
            }

            var incoming = page.journeys
            var hiddenArrivalJourneys = if (replaceResults) emptyList() else _state.value.hiddenArrivalJourneys
            var fetchedEarlierPagingEarlier: String? = null
            var fetchedLaterPagingLater: String? = null
            if (isInitialArrivalSearch && page.journeys.isNotEmpty()) {
                val trimmed = trimArrivalResultsForDisplay(page.journeys, whenTime)
                incoming = trimmed.visible
                hiddenArrivalJourneys = trimmed.hidden
            }
            publishSearchResults(
                existing = existing,
                existingIds = existingIds,
                incoming = incoming,
                hiddenArrivalJourneys = hiddenArrivalJourneys,
                page = page,
                fetchedEarlierPagingEarlier = fetchedEarlierPagingEarlier,
                fetchedLaterPagingLater = fetchedLaterPagingLater,
                replaceResults = replaceResults,
                prepend = prepend,
                options = options,
                generation = generation,
                isRefiningArrivalResults = false,
            )
        } catch (e: DbApiBlockedException) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefiningArrivalResults = false,
                    isLoadingEarlier = false,
                    isLoadingLater = false,
                    error = "error_api_blocked",
                )
            }
        } catch (e: DbParseException) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefiningArrivalResults = false,
                    isLoadingEarlier = false,
                    isLoadingLater = false,
                    error = "error_parse",
                )
            }
        } catch (e: DbApiException) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefiningArrivalResults = false,
                    isLoadingEarlier = false,
                    isLoadingLater = false,
                    error = "error_search_failed",
                )
            }
        } catch (e: IOException) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefiningArrivalResults = false,
                    isLoadingEarlier = false,
                    isLoadingLater = false,
                    error = "error_network",
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefiningArrivalResults = false,
                    isLoadingEarlier = false,
                    isLoadingLater = false,
                    error = "error_search_failed",
                )
            }
        }
    }

    private suspend fun refineInitialArrivalSearch(
        from: Location,
        to: Location,
        options: JourneySearchOptions,
        whenTime: LocalDateTime,
        initialPage: JourneySearchResult,
        generation: Long,
    ) {
        if (generation != searchGeneration) return
        val (earlierPage, laterPage) = coroutineScope {
            val earlier = async {
                initialPage.pagingEarlier?.let { token ->
                    searchUseCase.searchJourneys(from, to, options, whenTime, token)
                }
            }
            val later = async {
                initialPage.pagingLater?.let { token ->
                    searchUseCase.searchJourneys(from, to, options, whenTime, token)
                }
            }
            earlier.await() to later.await()
        }
        if (generation != searchGeneration) return
        val trimmed = trimArrivalResultsForDisplay(
            mergeArrivalSearchPages(
                initial = initialPage.journeys,
                earlier = earlierPage?.journeys.orEmpty(),
                later = laterPage?.journeys.orEmpty(),
            ),
            whenTime,
        )
        val previousIds = _state.value.journeys.map { it.id }.toSet()
        publishSearchResults(
            existing = _state.value.journeys,
            existingIds = previousIds,
            incoming = trimmed.visible,
            hiddenArrivalJourneys = trimmed.hidden,
            page = initialPage,
            fetchedEarlierPagingEarlier = earlierPage?.pagingEarlier,
            fetchedLaterPagingLater = laterPage?.pagingLater,
            replaceResults = false,
            prepend = false,
            options = options,
            generation = generation,
            isRefiningArrivalResults = false,
            replaceVisibleJourneys = true,
        )
    }

    private fun publishSearchResults(
        existing: List<Journey>,
        existingIds: Set<String>,
        incoming: List<Journey>,
        hiddenArrivalJourneys: List<Journey>,
        page: JourneySearchResult,
        fetchedEarlierPagingEarlier: String?,
        fetchedLaterPagingLater: String?,
        replaceResults: Boolean,
        prepend: Boolean,
        options: JourneySearchOptions,
        generation: Long,
        isRefiningArrivalResults: Boolean,
        replaceVisibleJourneys: Boolean = false,
    ) {
        val mergedJourneys = when {
            replaceVisibleJourneys -> incoming
            else -> mergeJourneys(existing, incoming, prepend)
        }
        val newJourneys = incoming.filter { it.id !in existingIds }
        val keptRated = when {
            replaceResults -> emptyList()
            replaceVisibleJourneys -> _state.value.ratedJourneys.filter { rated ->
                mergedJourneys.any { it.id == rated.journey.id }
            }
            else -> _state.value.ratedJourneys.filter { rated ->
                mergedJourneys.any { it.id == rated.journey.id }
            }
        }
        logSearchOutcome(mergedJourneys.size, mergedJourneys.isEmpty() && replaceResults)
        val scrollToken = if (replaceResults && mergedJourneys.isNotEmpty()) {
            _state.value.scrollToResultsToken + 1
        } else {
            _state.value.scrollToResultsToken
        }
        _state.update {
            it.copy(
                journeys = mergedJourneys,
                ratedJourneys = keptRated,
                hiddenArrivalJourneys = hiddenArrivalJourneys,
                pagingEarlier = when {
                    replaceResults || replaceVisibleJourneys ->
                        fetchedEarlierPagingEarlier ?: page.pagingEarlier
                    prepend -> page.pagingEarlier
                    else -> it.pagingEarlier
                },
                pagingLater = when {
                    replaceResults || replaceVisibleJourneys ->
                        fetchedLaterPagingLater ?: page.pagingLater
                    !prepend && page.pagingLater != null -> page.pagingLater
                    else -> it.pagingLater
                },
                isLoading = false,
                isRefiningArrivalResults = isRefiningArrivalResults,
                isLoadingEarlier = false,
                isLoadingLater = false,
                predictionsLoading = _state.value.showPredictions && newJourneys.isNotEmpty(),
                info = if (mergedJourneys.isEmpty() && replaceResults) {
                    noConnectionsInfoKey(options)
                } else {
                    null
                },
                scrollToResultsToken = scrollToken,
            )
        }
        if (_state.value.showPredictions && newJourneys.isNotEmpty()) {
            startPredictionsJob(newJourneys, prepend, generation)
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

    private fun startPredictionsJob(
        journeys: List<Journey>,
        prepend: Boolean,
        generation: Long,
    ) {
        predictionsJob?.cancel()
        predictionsJob = viewModelScope.launch {
            rateJourneysProgressively(journeys, prepend, generation)
        }
    }

    private suspend fun rateJourneysProgressively(
        journeys: List<Journey>,
        prepend: Boolean,
        generation: Long,
    ) {
        val options = currentRatingOptions()
        for (journey in journeys) {
            if (generation != searchGeneration) return
            val rated = runCatching { searchUseCase.rateJourney(journey, options) }
                .onFailure { e ->
                    OpenBahnDebugLog.w(
                        "Search",
                        "prediction failed for ${journey.id}: ${e.message}",
                        e,
                    )
                }
                .getOrNull() ?: continue
            if (generation != searchGeneration) return
            _state.update { state ->
                state.copy(
                    ratedJourneys = mergeRated(state.ratedJourneys, listOf(rated), prepend),
                )
            }
        }
        if (generation == searchGeneration) {
            _state.update { it.copy(predictionsLoading = false) }
            val count = _state.value.ratedJourneys.size
            OpenBahnDebugLog.d("Search", "predictions finished: $count rated journey(s)")
        }
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
        if (query.trim().length < 2) return null
        val recent = locationHistory.recentMatching(query)
        val apiResults = searchUseCase.searchLocations(query, _state.value.locale)
        val resolved = resolveLocationForSearch(query, selected, suggestions, recent, apiResults)
        if (resolved == null) {
            OpenBahnDebugLog.w(
                "Search",
                "resolveLocation($label) failed q=\"$query\" apiHits=${apiResults.size} " +
                    apiResults.take(3).joinToString { it.name },
            )
        }
        return resolved
    }

    private fun noConnectionsInfoKey(options: JourneySearchOptions): String = when {
        options.deutschlandTicketConnectionsOnly -> "info_no_connections_dticket"
        options.directOnly ||
            options.fastRoutesOnly ||
            options.products.size < TransportProduct.ALL.size ->
            "info_no_connections_filters"
        else -> "info_no_connections"
    }

    private fun loadSuggestions(query: String, isFrom: Boolean) {
        val field = if (isFrom) ActiveLocationField.FROM else ActiveLocationField.TO
        if (_state.value.activeLocationField != field) return
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            val local = locationHistory.rankedForAutocomplete(query)
            applySuggestions(field, local)
            if (query.trim().length < 2) return@launch
            delay(300)
            if (_state.value.activeLocationField != field) return@launch
            try {
                val api = searchUseCase.searchLocations(query, _state.value.locale)
                val merged = mergeWithApiResults(local, api, query)
                applySuggestions(field, merged)
            } catch (_: Exception) {
            }
        }
    }

    private fun applySuggestions(field: ActiveLocationField, suggestions: List<Location>) {
        _state.update { state ->
            val filtered = suggestions.excludingOppositeStation(field, state)
            when {
                field == ActiveLocationField.FROM && state.activeLocationField == ActiveLocationField.FROM ->
                    state.copy(fromSuggestions = filtered)
                field == ActiveLocationField.TO && state.activeLocationField == ActiveLocationField.TO ->
                    state.copy(toSuggestions = filtered)
                else -> state
            }
        }
    }

    private fun List<Location>.excludingOppositeStation(
        field: ActiveLocationField,
        state: SearchUiState,
    ): List<Location> = excludingStations(state, excludeField = field)

    private fun List<Location>.excludingStations(
        state: SearchUiState,
        excludeField: ActiveLocationField? = null,
        excludeViaIndex: Int? = null,
    ): List<Location> {
        val blocked = buildSet {
            if (excludeField != ActiveLocationField.FROM) state.from?.stableKey()?.let { add(it) }
            if (excludeField != ActiveLocationField.TO) state.to?.stableKey()?.let { add(it) }
            state.viaStops.forEachIndexed { index, via ->
                if (index != excludeViaIndex) via.location?.stableKey()?.let { add(it) }
            }
        }
        if (blocked.isEmpty()) return this
        return filter { it.stableKey() !in blocked }
    }

    private fun mergeWithApiResults(
        local: List<Location>,
        api: List<Location>,
        query: String,
    ): List<Location> {
        val q = query.trim()
        val seen = local.map { it.stableKey() }.toMutableSet()
        val fromApi = api
            .filter { it.stableKey() !in seen && it.name.matchesAutocompleteQuery(q) }
            .sortedWith(
                compareBy<Location> { it.name.autocompleteMatchRank(q) }
                    .thenBy { it.name.lowercase() },
            )
        return (local + fromApi).take(8)
    }
}
