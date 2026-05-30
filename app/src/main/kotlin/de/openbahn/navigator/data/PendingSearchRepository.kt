package de.openbahn.navigator.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Schedules a favorite-route search when the user opens the Search tab. */
class PendingSearchRepository {
    private val _pendingRoute = MutableStateFlow<FavoriteRoute?>(null)
    val pendingRoute: StateFlow<FavoriteRoute?> = _pendingRoute.asStateFlow()

    fun schedule(route: FavoriteRoute) {
        _pendingRoute.value = route
    }

    fun consume(): FavoriteRoute? {
        val route = _pendingRoute.value ?: return null
        _pendingRoute.value = null
        return route
    }
}
