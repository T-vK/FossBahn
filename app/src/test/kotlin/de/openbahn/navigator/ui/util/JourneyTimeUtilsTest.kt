package de.openbahn.navigator.ui.util

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JourneyTimeUtilsTest {
    @Test
    fun isJourneyLongArrived_trueWhenArrivalWasHoursAgo() {
        val journey = Journey(
            id = "1",
            legs = listOf(
                Leg(
                    origin = StopEvent("A", scheduledTime = "2020-01-01T08:00:00"),
                    destination = StopEvent("B", scheduledTime = "2020-01-01T10:00:00"),
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2020-01-01T08:00:00",
            arrival = "2020-01-01T10:00:00",
        )
        assertTrue(isJourneyLongArrived(journey, graceHours = 2, now = LocalDateTime.of(2026, 5, 30, 12, 0)))
    }

    @Test
    fun isJourneyLongArrived_falseForFutureJourney() {
        val future = LocalDateTime.now().plusHours(5)
        val iso = future.toString()
        val journey = Journey(
            id = "2",
            legs = listOf(
                Leg(
                    origin = StopEvent("A", scheduledTime = iso),
                    destination = StopEvent("B", scheduledTime = iso),
                ),
            ),
            durationMinutes = 60,
            transfers = 0,
            departure = iso,
            arrival = iso,
        )
        assertFalse(isJourneyLongArrived(journey))
    }
}
