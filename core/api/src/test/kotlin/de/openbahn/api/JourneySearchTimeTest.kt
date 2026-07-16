package de.openbahn.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JourneySearchTimeTest {
    @Test
    fun forApiRequest_bumpsPastDepartureToFuture() {
        val past = JourneySearchTime.nowBerlin().minusSeconds(30)
        val api = JourneySearchTime.forApiRequest(past)
        assertTrue(api.isAfter(JourneySearchTime.nowBerlin()))
    }

    @Test
    fun forApiRequest_keepsFutureDepartureVerbatim() {
        val future = JourneySearchTime.nowBerlin().plusHours(5).withNano(0)
        assertEquals(future, JourneySearchTime.forApiRequest(future))
    }

    @Test
    fun forApiRequest_keepsArrivalTimeVerbatim_evenWhenInThePast() {
        val pastArrival = JourneySearchTime.nowBerlin().minusHours(2).withNano(0)
        assertEquals(pastArrival, JourneySearchTime.forApiRequest(pastArrival, arrivalSearch = true))

        val futureArrival = JourneySearchTime.nowBerlin().plusHours(2).withNano(0)
        assertEquals(futureArrival, JourneySearchTime.forApiRequest(futureArrival, arrivalSearch = true))
    }
}
