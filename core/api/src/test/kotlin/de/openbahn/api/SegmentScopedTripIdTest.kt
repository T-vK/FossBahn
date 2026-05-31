package de.openbahn.api

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SegmentScopedTripIdTest {
    @Test
    fun detectsSegmentScopedDbTripId() {
        val id = "2|#VN#1#ST#1779908603#PI#0#ZI#445871#TA#0#DA#310526#1S#8002549#1T#1634#L"
        assertTrue(TripRouteFetcher.isSegmentScopedTripId(id))
    }

    @Test
    fun fullZuglaufIdIsNotSegmentScoped() {
        assertFalse(TripRouteFetcher.isSegmentScopedTripId("2|#ZB#RB    31#ZE#81633#"))
    }
}
