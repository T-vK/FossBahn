package de.openbahn.rights

import de.openbahn.rights.model.DelayEvent
import de.openbahn.rights.model.PassengerRightsDecisionState
import de.openbahn.rights.model.PlannedTrip
import de.openbahn.rights.model.RouteGraph
import de.openbahn.rights.model.TicketContext
import de.openbahn.rights.rules.ReplacementTransportRules
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReplacementTransportRulesTest {
    private val trip = PlannedTrip(
        journeyId = "j1",
        departureIso = "2026-05-31T16:00:00",
        arrivalIso = "2026-05-31T20:00:00",
        fromName = "A",
        toName = "B",
        ticketContext = TicketContext.DEUTSCHLAND_TICKET,
        railLegCount = 2,
        isLastConnectionOfDay = true,
    )

    @Test
    fun taxiPlausibleOnLastConnectionWithDelay() {
        val result = ReplacementTransportRules.evaluate(
            plannedTrip = trip,
            routeGraph = RouteGraph(trip, hasPublicAlternativeInWindow = false),
            delay = DelayEvent(
                recordedAtEpochMillis = 0L,
                arrivalDelayMinutes = 75,
                maxEnRouteDelayMinutes = 75,
                anyCancellation = false,
                missedTransferCount = 0,
                destinationUnreachableBeforeMidnight = true,
            ),
        )
        assertTrue(PassengerRightsDecisionState.TAXI_REIMBURSEMENT_POSSIBLE in result.states)
        assertTrue(result.exceptions.any { it.requiresUserConfirmation })
    }

    @Test
    fun noTaxiOnMinorDelay() {
        val result = ReplacementTransportRules.evaluate(
            plannedTrip = trip.copy(isLastConnectionOfDay = false),
            routeGraph = RouteGraph(trip, hasPublicAlternativeInWindow = true),
            delay = DelayEvent(
                recordedAtEpochMillis = 0L,
                arrivalDelayMinutes = 15,
                maxEnRouteDelayMinutes = 15,
                anyCancellation = false,
                missedTransferCount = 0,
                destinationUnreachableBeforeMidnight = false,
            ),
        )
        assertFalse(PassengerRightsDecisionState.TAXI_REIMBURSEMENT_POSSIBLE in result.states)
    }
}
