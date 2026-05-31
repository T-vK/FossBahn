package de.openbahn.navigator.ui.search

import de.openbahn.model.Location
import de.openbahn.model.LocationType

/**
 * Picks origin/destination for journey search from UI state and /orte results.
 * Prefer stations and exact name matches; avoid ambiguous POI-only hits.
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
    stations.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }

    val exactAny = apiResults.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
    if (exactAny != null) {
        if (exactAny.type == LocationType.STATION || stations.isEmpty()) return exactAny
    }

    if (trimmed.length >= 4) {
        stations.firstOrNull { it.name.startsWith(trimmed, ignoreCase = true) }?.let { return it }
    }
    if (stations.size == 1) return stations.first()
    if (apiResults.size == 1) return apiResults.first()
    return null
}
