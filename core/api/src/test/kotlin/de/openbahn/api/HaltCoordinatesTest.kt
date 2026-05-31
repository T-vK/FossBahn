package de.openbahn.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HaltCoordinatesTest {
    @Test
    fun parsesMicroDegreeCoordinatesFromHaltId() {
        val haltId = "A=1@O=Köln Hbf@X=6958730@Y=50943029@L=8000207@"
        val (lat, lon) = coordinatesFromHaltId(haltId)!!
        assertEquals(50.943029, lat, 0.0001)
        assertEquals(6.958730, lon, 0.0001)
    }

    @Test
    fun returnsNullWithoutCoordinates() {
        assertNull(coordinatesFromHaltId("A=1@L=8000207@"))
    }
}
