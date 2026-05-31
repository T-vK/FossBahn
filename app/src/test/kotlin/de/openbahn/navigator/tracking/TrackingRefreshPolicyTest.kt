package de.openbahn.navigator.tracking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TrackingRefreshPolicyTest {

    @Test
    fun nearDeparture_usesConfiguredSeconds() {
        val now = LocalDateTime.of(2026, 5, 30, 12, 0)
        val departure = now.plusMinutes(10)
        val delay = TrackingRefreshPolicy.delayUntilNextCheckMillis(
            departureTimes = listOf(departure),
            nearDepartureIntervalSeconds = 5,
            now = now,
        )
        assertEquals(5_000L, delay)
    }

    @Test
    fun farDeparture_usesLongInterval() {
        val now = LocalDateTime.of(2026, 5, 30, 12, 0)
        val departure = now.plusHours(48)
        val delay = TrackingRefreshPolicy.delayUntilNextCheckMillis(
            departureTimes = listOf(departure),
            nearDepartureIntervalSeconds = 5,
            now = now,
        )
        assertEquals(30 * 60 * 1000L, delay)
    }
}
