package de.openbahn.navigator.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.api.DbVendoClient
import de.openbahn.model.BoardEntry
import de.openbahn.model.Location
import de.openbahn.model.StationBoard
import de.openbahn.model.TransportProduct
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class BoardUiState(
    val query: String = "",
    val station: Location? = null,
    val suggestions: List<Location> = emptyList(),
    val board: StationBoard? = null,
    val products: Set<TransportProduct> = TransportProduct.ALL,
    val showArrivals: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class StationBoardViewModel(private val client: DbVendoClient) : ViewModel() {
    private val _state = MutableStateFlow(BoardUiState())
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        if (query.length >= 2) {
            viewModelScope.launch {
                try {
                    val results = client.searchLocations(query)
                    _state.update { it.copy(suggestions = results) }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun selectStation(station: Location) {
        _state.update { it.copy(station = station, query = station.name, suggestions = emptyList()) }
        loadBoard()
    }

    fun setProducts(products: Set<TransportProduct>) {
        _state.update { it.copy(products = products) }
        loadBoard()
    }

    fun toggleArrivals(show: Boolean) {
        _state.update { it.copy(showArrivals = show) }
    }

    fun loadBoard() {
        val station = _state.value.station ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val board = client.stationBoard(
                    station,
                    LocalDateTime.now(),
                    products = _state.value.products,
                )
                _state.update { it.copy(board = board, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun currentEntries(): List<BoardEntry> {
        val board = _state.value.board ?: return emptyList()
        return if (_state.value.showArrivals) board.arrivals else board.departures
    }
}
