package de.openbahn.api

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BahnVorhersageTripPayloadTest {
    @Test
    fun ratingTripId_isUniquePerLeg_evenWhenDbTripIdRepeats() {
        val sharedDbTrip = "2|#VN#same#"
        val journey = Journey(
            id = "j-dup",
            legs = listOf(
                Leg(
                    origin = StopEvent("A", scheduledTime = "2026-06-01T10:00:00"),
                    destination = StopEvent("B", scheduledTime = "2026-06-01T11:00:00"),
                    tripId = sharedDbTrip,
                ),
                Leg(
                    origin = StopEvent("B", scheduledTime = "2026-06-01T11:05:00"),
                    destination = StopEvent("C", scheduledTime = "2026-06-01T12:00:00"),
                    tripId = sharedDbTrip,
                ),
            ),
            durationMinutes = 120,
            transfers = 1,
            departure = "2026-06-01T10:00:00",
            arrival = "2026-06-01T12:00:00",
        )
        assertEquals("openbahn-j-dup-leg0", ratingTripId(journey.id, 0, journey.legs[0]))
        assertEquals("openbahn-j-dup-leg1", ratingTripId(journey.id, 1, journey.legs[1]))
        val trips = buildTripRoutesForRating(journey)
        assertEquals(2, trips.size)
        assertEquals("A", trips["openbahn-j-dup-leg0"]!!.first().name)
        assertEquals("B", trips["openbahn-j-dup-leg0"]!!.last().name)
        assertEquals("B", trips["openbahn-j-dup-leg1"]!!.first().name)
        assertEquals("C", trips["openbahn-j-dup-leg1"]!!.last().name)
    }

    @Test
    fun buildTripRoutesForRating_usesOnlyLegEndpoints_notIntermediateHalte() {
        val journey = Journey(
            id = "j-mid",
            legs = listOf(
                Leg(
                    origin = StopEvent("Hamburg Hbf", scheduledTime = "2026-06-01T10:00:00"),
                    destination = StopEvent("Berlin Hbf", scheduledTime = "2026-06-01T12:00:00"),
                    intermediateStops = listOf(
                        StopEvent("Hannover Hbf", scheduledTime = "2026-06-01T11:00:00"),
                    ),
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2026-06-01T10:00:00",
            arrival = "2026-06-01T12:00:00",
        )
        val trips = buildTripRoutesForRating(journey)
        assertEquals(1, trips.size)
        assertEquals(2, trips.values.first().size)
        assertEquals("Hamburg Hbf", trips.values.first().first().name)
        assertEquals("Berlin Hbf", trips.values.first().last().name)
    }

    @Test
    fun ensureLegEndpoints_alignsFirstAndLastStopNames() {
        val leg = Leg(
            origin = StopEvent("Hamburg Hbf", scheduledTime = "2026-06-01T10:00:00"),
            destination = StopEvent("Berlin Hbf", scheduledTime = "2026-06-01T12:00:00"),
        )
        val misnamed = listOf(
            StopEvent("Hamburg-Hbf", scheduledTime = "2026-06-01T10:00:00"),
            StopEvent("Spandau", scheduledTime = "2026-06-01T11:00:00"),
            StopEvent("Berlin-Hbf", scheduledTime = "2026-06-01T12:00:00"),
        )
        val fixed = ensureLegEndpoints(misnamed, leg)
        assertEquals("Hamburg Hbf", fixed.first().name)
        assertEquals("Berlin Hbf", fixed.last().name)
    }
}
