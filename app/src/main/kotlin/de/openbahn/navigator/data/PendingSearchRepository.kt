package de.openbahn.navigator.data

import de.openbahn.model.Location
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AlternativesSearchRequest(
    val from: Location,
    val to: Location,
    val departureTime: LocalDateTime,
    val arrivalSearch: Boolean,
)

/** Schedules a favorite-route or alternatives search when the user opens the Search tab. */
class PendingSearchRepository {
    private val _pendingRoute = MutableStateFlow<FavoriteRoute?>(null)
    val pendingRoute: StateFlow<FavoriteRoute?> = _pendingRoute.asStateFlow()

    private val _pendingAlternatives = MutableStateFlow<AlternativesSearchRequest?>(null)
    val pendingAlternatives: StateFlow<AlternativesSearchRequest?> = _pendingAlternatives.asStateFlow()

    fun schedule(route: FavoriteRoute) {
        _pendingRoute.value = route
    }

    fun scheduleAlternatives(request: AlternativesSearchRequest) {
        _pendingAlternatives.value = request
    }

    fun consume(): FavoriteRoute? {
        val route = _pendingRoute.value ?: return null
        _pendingRoute.value = null
        return route
    }

    fun consumeAlternatives(): AlternativesSearchRequest? {
        val request = _pendingAlternatives.value ?: return null
        _pendingAlternatives.value = null
        return request
    }
}
