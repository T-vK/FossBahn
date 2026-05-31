package de.openbahn.navigator.tracking

import java.time.Duration
import java.time.LocalDateTime

/**
 * Adaptive delay-check interval based on time until the soonest tracked departure.
 * Reduces API and mobile data use for journeys far in the future.
 */
object TrackingRefreshPolicy {
    private const val NEAR_DEPARTURE_THRESHOLD_MINUTES = 20L

    fun delayUntilNextCheckMillis(
        departureTimes: List<LocalDateTime>,
        nearDepartureIntervalSeconds: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): Long {
        if (departureTimes.isEmpty()) return 15 * 60 * 1000L
        val soonestMinutes = departureTimes.minOf { departure ->
            Duration.between(now, departure).toMinutes().coerceAtLeast(0)
        }
        val intervalMs = when {
            soonestMinutes < NEAR_DEPARTURE_THRESHOLD_MINUTES ->
                nearDepartureIntervalSeconds.coerceIn(5, 120) * 1000L
            soonestMinutes < 120 -> 2 * 60 * 1000L
            soonestMinutes < 360 -> 5 * 60 * 1000L
            soonestMinutes < 1_440 -> 15 * 60 * 1000L
            else -> 30 * 60 * 1000L
        }
        return intervalMs.coerceAtLeast(5_000L)
    }
}
