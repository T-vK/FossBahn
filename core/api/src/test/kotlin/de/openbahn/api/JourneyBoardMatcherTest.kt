package de.openbahn.api

import de.openbahn.api.mapper.JourneyBoardMatcher
import de.openbahn.model.BoardEntry
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class JourneyBoardMatcherTest {
    @Test
    fun findDepartureMatch_byTripId() {
        val tripId = "2|#VN#1#ST#1738783727#PI#0#ZI#315246#"
        val leg = Leg(
            origin = StopEvent("A", scheduledTime = "2025-02-08T15:31:00"),
            destination = StopEvent("B", scheduledTime = "2025-02-08T17:00:00"),
            lineName = "RE 19073",
            tripId = tripId,
        )
        val board = BoardEntry(
            line = "RE 19073",
            direction = "Stuttgart",
            scheduledTime = "2025-02-08T15:31:00",
            prognosedTime = "2025-02-08T16:05:00",
            delayMinutes = 34,
            tripId = tripId,
        )
        assertNotNull(JourneyBoardMatcher.findDepartureMatch(listOf(board), leg))
        assertEquals(34, JourneyBoardMatcher.boardDelayMinutes(board))
    }
}
