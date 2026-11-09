package de.openbahn.navigator.ui.search

import de.openbahn.model.Location
import de.openbahn.model.LocationType

/**
 * Picks origin/destination for journey search from UI state and /orte results.
 * Prefer stations when the query is ambiguous, but keep exact address/POI matches
 * so door-to-door searches (with walking legs) still resolve.
 */
internal fun resolveLocationForSearch(
    query: String,
    selected: Location?,
    suggestions: List<Location>,
    recent: List<Location>,
    apiResults: List<Location>,
): Location? {
    val trimmed = query.trim()
    if (trimmed.length < 2) return null

    if (selected != null && selected.name.equals(trimmed, ignoreCase = true)) {
        return selected
    }
    suggestions.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
    recent.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }

    val stations = apiResults.filter { it.type == LocationType.STATION }
    val addresses = apiResults.filter { it.type == LocationType.ADDRESS }
    stations.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }

    val exactAddress = addresses.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
    if (exactAddress != null) {
        // City-name POIs like "Berlin" must not beat "Berlin Hbf" / other station prefixes.
        val stationCompetes = stations.any {
            it.name.startsWith(trimmed, ignoreCase = true) ||
                it.name.contains(trimmed, ignoreCase = true)
        }
        if (!stationCompetes) return exactAddress
    }

    val exactAny = apiResults.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
    if (exactAny != null && exactAny.type == LocationType.STATION) return exactAny

    if (trimmed.length >= 4) {
        stations.firstOrNull { it.name.startsWith(trimmed, ignoreCase = true) }?.let { return it }
        addresses.firstOrNull { it.name.startsWith(trimmed, ignoreCase = true) }?.let { return it }
    }
    if (stations.size == 1 && addresses.isEmpty()) return stations.first()
    if (apiResults.size == 1) return apiResults.first()
    return null
}
