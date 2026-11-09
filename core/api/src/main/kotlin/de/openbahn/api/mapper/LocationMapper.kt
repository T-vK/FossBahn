package de.openbahn.api.mapper

import de.openbahn.api.coordinatesFromHaltId
import de.openbahn.api.dto.DbLocationResponse
import de.openbahn.api.dto.DbOrt
import de.openbahn.api.locationTypeFromHaltId
import de.openbahn.model.Location
import de.openbahn.model.LocationType

internal object LocationMapper {
    fun mapLocations(response: DbLocationResponse): List<Location> {
        val items = response.orte.orEmpty() + response.locations.orEmpty()
        return mapOrtList(items)
    }

    fun mapOrtList(items: List<DbOrt>): List<Location> =
        items.mapNotNull(::mapOrt).distinctBy { it.id }

    private fun mapOrt(ort: DbOrt): Location? {
        val id = ort.id ?: ort.extId ?: return null
        val name = ort.name ?: return null
        val type = resolveLocationType(ort.type, id)
        val coordsFromId = coordinatesFromHaltId(id)
        val lat = ort.lat ?: ort.coordinate?.y ?: coordsFromId?.first
        val lon = ort.lon ?: ort.coordinate?.x ?: coordsFromId?.second
        // Address/POI lids may carry a non-EVA @L= token — never treat it as a station id.
        val eva = ort.extId?.takeIf { type == LocationType.STATION && it.all(Char::isDigit) }
        return Location(
            id = id,
            name = name,
            type = type,
            latitude = lat,
            longitude = lon,
            evaNumber = eva,
        )
    }

    private fun resolveLocationType(rawType: String?, haltId: String): LocationType {
        locationTypeFromHaltId(haltId)?.let { return it }
        return when (rawType?.uppercase()) {
            "POI", "ADR", "ADDRESS", "LOC" -> LocationType.ADDRESS
            "ST", "S", "STATION", "STOP" -> LocationType.STATION
            else -> LocationType.STATION
        }
    }
}
