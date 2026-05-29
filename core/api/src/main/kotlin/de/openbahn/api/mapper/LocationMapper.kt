package de.openbahn.api.mapper

import de.openbahn.api.dto.DbLocationResponse
import de.openbahn.api.dto.DbOrt
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
        val lat = ort.lat ?: ort.coordinate?.y
        val lon = ort.lon ?: ort.coordinate?.x
        return Location(
            id = id,
            name = name,
            type = when (ort.type?.uppercase()) {
                "POI", "ADR" -> LocationType.ADDRESS
                "ST" -> LocationType.STATION
                else -> LocationType.STATION
            },
            latitude = lat,
            longitude = lon,
            evaNumber = ort.extId,
        )
    }
}
