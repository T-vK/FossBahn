package de.openbahn.navigator.tracking

import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class TrackingLocationsTest {
    @Test
    fun toTrackingLocation_extractsEvaFromHaltId() {
        val loc = StopEvent(
            name = "Berlin Hbf",
            id = "A=1@O=Berlin@L=8011160@",
            scheduledTime = "2026-05-30T10:00:00",
        ).toTrackingLocation()

        assertNotNull(loc)
        assertEquals("8011160", loc?.evaNumber)
    }
}
