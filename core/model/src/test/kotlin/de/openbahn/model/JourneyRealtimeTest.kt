package de.openbahn.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JourneyRealtimeTest {
    @Test
    fun delayMinutesFromTimes_computesPositiveDelay() {
        assertEquals(7, delayMinutesFromTimes("2026-05-30T12:00:00", "2026-05-30T12:07:00"))
    }

    @Test
    fun delayMinutesFromTimes_returnsNullWhenOnTime() {
        assertNull(delayMinutesFromTimes("2026-05-30T12:00:00", "2026-05-30T12:00:00"))
    }

    @Test
    fun withRealtimeFrom_prefersRefreshedDelay() {
        val search = Journey(
            id = "j1",
            legs = listOf(
                Leg(
                    origin = StopEvent("A", scheduledTime = "2026-05-30T12:00:00"),
                    destination = StopEvent("B", scheduledTime = "2026-05-30T14:00:00"),
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2026-05-30T12:00:00",
            arrival = "2026-05-30T14:00:00",
        )
        val refreshed = search.copy(
            legs = listOf(
                search.legs.single().copy(
                    origin = StopEvent(
                        "A",
                        scheduledTime = "2026-05-30T12:00:00",
                        prognosedTime = "2026-05-30T12:09:00",
                        delayMinutes = 9,
                    ),
                ),
            ),
        )
        val merged = search.withRealtimeFrom(refreshed)
        assertEquals("j1", merged.id)
        assertEquals(9, merged.legs.single().origin.delayMinutes)
    }

    @Test
    fun withRealtimeFrom_appliesPlatformChangeWithoutDelayUpdate() {
        val search = Journey(
            id = "j1",
            legs = listOf(
                Leg(
                    origin = StopEvent(
                        "A",
                        scheduledTime = "2026-05-30T12:00:00",
                        platform = "3",
                    ),
                    destination = StopEvent("B", scheduledTime = "2026-05-30T14:00:00"),
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2026-05-30T12:00:00",
            arrival = "2026-05-30T14:00:00",
        )
        val refreshed = search.copy(
            legs = listOf(
                search.legs.single().copy(
                    origin = search.legs.single().origin.copy(platform = "7"),
                ),
            ),
        )

        val merged = search.withRealtimeFrom(refreshed)

        assertEquals("7", merged.legs.single().origin.platform)
        assertNull(merged.legs.single().origin.delayMinutes)
    }

    @Test
    fun withRealtimeFrom_keepsSearchDirectionWhenRefreshOmitsIt() {
        val search = Journey(
            id = "j1",
            legs = listOf(
                Leg(
                    origin = StopEvent("A", scheduledTime = "2026-05-30T12:00:00"),
                    destination = StopEvent("B", scheduledTime = "2026-05-30T14:00:00"),
                    direction = "München Hbf",
                ),
            ),
            durationMinutes = 120,
            transfers = 0,
            departure = "2026-05-30T12:00:00",
            arrival = "2026-05-30T14:00:00",
        )
        val refreshed = search.copy(
            legs = listOf(search.legs.single().copy(direction = null)),
        )
        val merged = search.withRealtimeFrom(refreshed)
        assertEquals("München Hbf", merged.legs.single().direction)
    }

    @Test
    fun withBoardRealtime_appliesPlatformWithoutDelay() {
        val stop = StopEvent("A", scheduledTime = "2026-05-30T12:00:00", platform = "3")

        val merged = stop.withBoardRealtime(
            scheduled = "2026-05-30T12:00:00",
            prognosed = null,
            delayMinutes = null,
            platform = "7",
        )

        assertEquals("7", merged.platform)
        assertNull(merged.delayMinutes)
    }

    @Test
    fun withRealtimeFrom_mergesIntermediateStopDelaysByStationName() {
        val search = Journey(
            id = "j1",
            legs = listOf(
                Leg(
                    origin = StopEvent("Hamburg Hbf", scheduledTime = "2026-06-01T08:00:00", id = "8002549"),
                    destination = StopEvent("Berlin Hbf", scheduledTime = "2026-06-01T11:00:00", id = "8011160"),
                    intermediateStops = listOf(
                        StopEvent("Hannover Hbf", scheduledTime = "2026-06-01T09:20:00", id = "8000152"),
                    ),
                ),
            ),
            durationMinutes = 180,
            transfers = 0,
            departure = "2026-06-01T08:00:00",
            arrival = "2026-06-01T11:00:00",
        )
        val refreshed = search.copy(
            legs = listOf(
                search.legs.single().copy(
                    routeStops = listOf(
                        StopEvent("Hamburg Hbf", scheduledTime = "2026-06-01T08:00:00", id = "8002549"),
                        StopEvent(
                            "Hannover Hbf",
                            scheduledTime = "2026-06-01T09:20:00",
                            prognosedTime = "2026-06-01T09:28:00",
                            delayMinutes = 8,
                            id = "8000152",
                            platform = "5",
                        ),
                        StopEvent("Berlin Hbf", scheduledTime = "2026-06-01T11:00:00", id = "8011160"),
                    ),
                ),
            ),
        )

        val merged = search.withRealtimeFrom(refreshed)

        val via = merged.legs.single().intermediateStops.single()
        assertEquals(8, via.delayMinutes)
        assertEquals("5", via.platform)
        assertEquals("2026-06-01T09:28:00", via.prognosedTime)
    }

    @Test
    fun withRealtimeFrom_appliesOriginPlatformFromRouteStops() {
        val search = Journey(
            id = "j1",
            legs = listOf(
                Leg(
                    origin = StopEvent(
                        "Hamburg Hbf",
                        scheduledTime = "2026-06-01T08:00:00",
                        platform = "3",
                        id = "8002549",
                    ),
                    destination = StopEvent("Berlin Hbf", scheduledTime = "2026-06-01T11:00:00", id = "8011160"),
                ),
            ),
            durationMinutes = 180,
            transfers = 0,
            departure = "2026-06-01T08:00:00",
            arrival = "2026-06-01T11:00:00",
        )
        val refreshed = search.copy(
            legs = listOf(
                search.legs.single().copy(
                    origin = search.legs.single().origin.copy(platform = "3"),
                    routeStops = listOf(
                        StopEvent(
                            "Hamburg Hbf",
                            scheduledTime = "2026-06-01T08:00:00",
                            platform = "7",
                            id = "8002549",
                        ),
                        StopEvent("Berlin Hbf", scheduledTime = "2026-06-01T11:00:00", id = "8011160"),
                    ),
                ),
            ),
        )

        val merged = search.withRealtimeFrom(refreshed)

        assertEquals("7", merged.legs.single().origin.platform)
    }
}
