package de.openbahn.navigator.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.navigator.data.FavoriteRoute
import de.openbahn.navigator.data.FavoriteRouteRepository
import de.openbahn.navigator.data.LocationHistoryRepository
import de.openbahn.navigator.data.PendingSearchRepository
import de.openbahn.model.Location
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val favoriteRoutes: FavoriteRouteRepository,
    private val locationHistory: LocationHistoryRepository,
    private val pendingSearch: PendingSearchRepository,
) : ViewModel() {
    val routes = favoriteRoutes.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteLocations = locationHistory.observeFavoriteLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun searchRoute(route: FavoriteRoute) {
        pendingSearch.schedule(route)
    }

    fun deleteRoute(id: String) {
        viewModelScope.launch { favoriteRoutes.delete(id) }
    }

    fun removeFavoriteLocation(location: Location) {
        viewModelScope.launch { locationHistory.removeFavoriteLocation(location) }
    }
}
