package de.openbahn.rights.journey

import de.openbahn.model.Journey
import de.openbahn.model.TransportProduct
import de.openbahn.model.arrivalDelayMinutes
import de.openbahn.model.hasRailCancellation
import de.openbahn.model.maxDelayMinutes
import de.openbahn.model.missedTransferCount
import de.openbahn.model.railLegs
import de.openbahn.rights.model.DelayEvent
import de.openbahn.rights.model.PlannedTrip
import de.openbahn.rights.model.RouteGraph
import de.openbahn.rights.model.TicketContext
import de.openbahn.rights.stream.TripEventStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val berlinZone = ZoneId.of("Europe/Berlin")
private val isoLocalFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

object JourneyRightsAdapter {
    fun toTripEventStream(
        journey: Journey,
        ticketContext: TicketContext,
        recordedAtEpochMillis: Long = System.currentTimeMillis(),
        minTransferMinutes: Int = 0,
        isLastConnectionOfDay: Boolean = false,
        hasPublicAlternativeInWindow: Boolean = true,
    ): TripEventStream {
        val planned = toPlannedTrip(
            journey = journey,
            ticketContext = ticketContext,
            isLastConnectionOfDay = isLastConnectionOfDay,
        )
        val routeGraph = RouteGraph(
            plannedTrip = planned,
            hasPublicAlternativeInWindow = hasPublicAlternativeInWindow,
        )
        val delay = toDelayEvent(
            journey = journey,
            recordedAtEpochMillis = recordedAtEpochMillis,
            minTransferMinutes = minTransferMinutes,
            hasPublicAlternativeInWindow = hasPublicAlternativeInWindow,
        )
        return TripEventStream(
            plannedTrip = planned,
            routeGraph = routeGraph,
            delayEvents = listOf(delay),
        )
    }

    fun toPlannedTrip(
        journey: Journey,
        ticketContext: TicketContext,
        isLastConnectionOfDay: Boolean = false,
    ): PlannedTrip {
        val rail = journey.railLegs()
        return PlannedTrip(
            journeyId = journey.id,
            departureIso = journey.departure,
            arrivalIso = journey.arrival,
            fromName = rail.firstOrNull()?.origin?.name ?: journey.departure,
            toName = rail.lastOrNull()?.destination?.name ?: journey.arrival,
            ticketContext = ticketContext,
            railLegCount = rail.size,
            isLastConnectionOfDay = isLastConnectionOfDay,
            usesLongDistanceRail = rail.any { leg ->
                leg.product in LONG_DISTANCE_PRODUCTS
            },
            deutschlandTicketMarkedValid = journey.deutschlandTicketValid,
        )
    }

    fun toDelayEvent(
        journey: Journey,
        recordedAtEpochMillis: Long,
        minTransferMinutes: Int,
        hasPublicAlternativeInWindow: Boolean,
    ): DelayEvent {
        val arrivalDelay = journey.arrivalDelayMinutes()
        val unreachable = isDestinationUnreachableBeforeMidnight(journey, arrivalDelay) &&
            !hasPublicAlternativeInWindow
        return DelayEvent(
            recordedAtEpochMillis = recordedAtEpochMillis,
            arrivalDelayMinutes = arrivalDelay,
            maxEnRouteDelayMinutes = journey.maxDelayMinutes(),
            anyCancellation = journey.hasRailCancellation(),
            missedTransferCount = journey.missedTransferCount(minTransferMinutes),
            destinationUnreachableBeforeMidnight = unreachable,
        )
    }

    fun resolveTicketContext(
        journey: Journey,
        userOwnsDeutschlandTicket: Boolean,
    ): TicketContext = when {
        userOwnsDeutschlandTicket || journey.deutschlandTicketValid == true ->
            TicketContext.DEUTSCHLAND_TICKET
        journey.railLegs().any { it.product in LONG_DISTANCE_PRODUCTS } ->
            TicketContext.STANDARD_LONG_DISTANCE
        else -> TicketContext.STANDARD_REGIONAL
    }

    private fun isDestinationUnreachableBeforeMidnight(journey: Journey, arrivalDelayMinutes: Int): Boolean {
        val scheduled = parseBerlinLocal(journey.arrival) ?: return false
        val arrival = scheduled.plusMinutes(arrivalDelayMinutes.toLong())
        val endOfTravelDay = scheduled.toLocalDate().atTime(23, 59, 59)
        return arrival.isAfter(endOfTravelDay)
    }

    private fun parseBerlinLocal(iso: String): LocalDateTime? = runCatching {
        LocalDateTime.parse(iso.take(19), isoLocalFormatter)
    }.getOrNull()

    private val LONG_DISTANCE_PRODUCTS = setOf(
        TransportProduct.ICE,
        TransportProduct.IC_EC,
        TransportProduct.IR,
    )
}
