package de.openbahn.navigator.ui.search

import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransferTimeBumpTest {

    @Test
    fun nextStay_stepsLikeBahnDeButton() {
        assertEquals(10, nextTransferStayMinutes(scheduledTransferMinutes = 7))
        assertEquals(15, nextTransferStayMinutes(scheduledTransferMinutes = 10))
        assertEquals(15, nextTransferStayMinutes(scheduledTransferMinutes = 7, currentStayMinutes = 10))
        assertEquals(20, nextTransferStayMinutes(scheduledTransferMinutes = 13, currentStayMinutes = 15))
        assertNull(nextTransferStayMinutes(scheduledTransferMinutes = 35))
    }

    @Test
    fun transferStationLocation_prefersEvaFromArrivalStop() {
        val arrival = StopEvent(
            name = "Hamburg Hbf",
            id = "A=1@O=Hamburg Hbf@L=8002549@",
            scheduledTime = "2026-05-30T12:00:00",
        )
        val departure = StopEvent(
            name = "Hamburg Hbf",
            id = "8002549",
            scheduledTime = "2026-05-30T12:20:00",
        )
        val location = transferStationLocation(arrival, departure)
        assertEquals("8002549", location?.evaNumber)
        assertEquals("Hamburg Hbf", location?.name)
    }

    @Test
    fun locationFromStopEvent_readsBareEva() {
        val stop = StopEvent(name = "Lüneburg", id = "8000238", scheduledTime = "2026-05-30T12:00:00")
        val location = locationFromStopEvent(stop)
        assertEquals("8000238", location?.evaNumber)
        assertTrue(location?.id == "8000238")
    }
}
