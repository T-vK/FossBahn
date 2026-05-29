package de.openbahn.api.mapper

import de.openbahn.api.dto.DbLocationResponse
import de.openbahn.api.dto.DbOrt
import de.openbahn.model.Location
import de.openbahn.model.LocationType

internal object LocationMapper {
    fun mapLocations(response: DbLocationResponse): List<Location> {
        val items = response.orte.orEmpty() + response.locations.orEmpty()
        return items.mapNotNull(::mapOrt).distinctBy { it.id }
    }

    private fun mapOrt(ort: DbOrt): Location? {
        val id = ort.id ?: ort.extId ?: return null
        val name = ort.name ?: return null
        return Location(
            id = id,
            name = name,
            type = when (ort.type?.uppercase()) {
                "POI", "ADR" -> LocationType.ADDRESS
                "ST" -> LocationType.STATION
                else -> LocationType.STATION
            },
            latitude = ort.coordinate?.y,
            longitude = ort.coordinate?.x,
            evaNumber = ort.extId,
        )
    }
}
