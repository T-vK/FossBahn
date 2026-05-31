package de.openbahn.rights.stream

import de.openbahn.rights.model.DelayEvent
import de.openbahn.rights.model.PlannedTrip
import de.openbahn.rights.model.RouteGraph

/**
 * Ordered inputs for the rights engine — separates raw trip facts from rule output.
 */
data class TripEventStream(
    val plannedTrip: PlannedTrip,
    val routeGraph: RouteGraph,
    val delayEvents: List<DelayEvent>,
) {
    val latestDelay: DelayEvent? get() = delayEvents.maxByOrNull { it.recordedAtEpochMillis }

    fun appendDelay(event: DelayEvent): TripEventStream =
        copy(delayEvents = delayEvents + event)
}
