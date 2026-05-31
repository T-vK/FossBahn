package de.openbahn.navigator.tracking

import de.openbahn.model.Journey
import de.openbahn.model.Location
import de.openbahn.model.StopEvent

internal fun StopEvent.toTrackingLocation(): Location? {
    val stopId = id?.takeIf { it.isNotBlank() } ?: return null
    val eva = Regex("""@L=(\d+)@""").find(stopId)?.groupValues?.getOrNull(1)
        ?: stopId.takeIf { it.all(Char::isDigit) && it.length >= 6 }
    return Location(
        id = stopId,
        name = name,
        evaNumber = eva,
    )
}

internal fun Journey.trackingEndpoints(): Pair<Location?, Location?> {
    val firstLeg = legs.firstOrNull() ?: return null to null
    val lastLeg = legs.lastOrNull() ?: return null to null
    return firstLeg.origin.toTrackingLocation() to lastLeg.destination.toTrackingLocation()
}
