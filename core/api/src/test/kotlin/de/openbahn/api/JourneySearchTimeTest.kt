package de.openbahn.api

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JourneySearchTimeTest {
    @Test
    fun forApiRequest_bumpsPastTimesToFuture() {
        val past = JourneySearchTime.nowBerlin().minusSeconds(30)
        val api = JourneySearchTime.forApiRequest(past)
        assertTrue(api.isAfter(JourneySearchTime.nowBerlin()))
    }
}
