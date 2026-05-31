package de.openbahn.rights

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.PassengerRightsSimulationPreset
import de.openbahn.model.StopEvent
import de.openbahn.model.applyPassengerRightsSimulation
import de.openbahn.model.toConfig
import de.openbahn.rights.engine.PassengerRightsEngine
import de.openbahn.rights.journey.JourneyRightsAdapter
import de.openbahn.rights.model.PassengerRightsDecisionState
import de.openbahn.rights.model.TicketContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PassengerRightsSimulationIntegrationTest {
    private val onTimeJourney = Journey(
        id = "sim-test",
        legs = listOf(
            Leg(
                origin = StopEvent("Hamburg Hbf", scheduledTime = "2026-05-31T16:00:00"),
                destination = StopEvent("Lüneburg", scheduledTime = "2026-05-31T17:00:00"),
            ),
        ),
        durationMinutes = 60,
        transfers = 0,
        departure = "2026-05-31T16:00:00",
        arrival = "2026-05-31T17:00:00",
        deutschlandTicketValid = true,
    )

    @Test
    fun simulatedDticketDelay65_triggersCompensation() {
        val config = PassengerRightsSimulationPreset.DTICKET_DELAY_60.toConfig()
        val simulated = onTimeJourney.applyPassengerRightsSimulation(config)
        val stream = JourneyRightsAdapter.toTripEventStream(
            journey = simulated,
            ticketContext = TicketContext.DEUTSCHLAND_TICKET,
        )
        val assessment = PassengerRightsEngine.evaluate(stream)
        assertTrue(PassengerRightsDecisionState.COMPENSATION_ELIGIBLE in assessment.decisionStates)
    }
}
