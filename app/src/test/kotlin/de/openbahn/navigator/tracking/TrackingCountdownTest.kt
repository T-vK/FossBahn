package de.openbahn.navigator.tracking

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrackingCountdownTest {

    @Test
    fun formatTrackingCountdown_formatsMinutesAndSeconds() {
        assertEquals("0:00", formatTrackingCountdown(0))
        assertEquals("1:05", formatTrackingCountdown(65))
        assertEquals("12:34", formatTrackingCountdown(754))
        assertEquals("59:59", formatTrackingCountdown(3599))
    }

    @Test
    fun formatTrackingCountdown_formatsHoursWhenOverOneHour() {
        assertEquals("1:00:00", formatTrackingCountdown(3600))
        assertEquals("1:05:30", formatTrackingCountdown(3930))
        assertEquals("2:00:01", formatTrackingCountdown(7201))
        assertEquals("10:05:03", formatTrackingCountdown(36_303))
    }

    @Test
    fun countdownSecondsUntil_beforeDeparture_countsToDeparture() {
        val journey = journey(
            dep = "2026-05-30T12:30:00",
            arr = "2026-05-30T16:00:00",
        )
        assertEquals(240, countdownSecondsUntil(journey, LocalDateTime.of(2026, 5, 30, 12, 26, 0)))
    }

    @Test
    fun countdownSecondsUntil_afterDeparture_staysAtZero() {
        val journey = journey(
            dep = "2026-05-30T12:30:00",
            arr = "2026-05-30T16:00:00",
        )
        assertEquals(0, countdownSecondsUntil(journey, LocalDateTime.of(2026, 5, 30, 15, 0, 0)))
    }

    @Test
    fun buildTrackingNotificationTitle_prefixesDepartureCountdownWithArrow() {
        val title = buildTrackingNotificationTitle(
            routeNames = listOf("Kiel", "Lübeck", "Hamburg"),
            baseTitle = "Kiel -> Lübeck -> Hamburg",
            countdown = TrackingTitleCountdown(
                kind = TrackingCountdownKind.DEPARTURE,
                seconds = 754,
            ),
            maxLength = 50,
        )
        assertEquals("↑ 12:34 · Kiel -> Lübeck -> Hamburg", title)
    }

    @Test
    fun buildTrackingNotificationTitle_prefixesArrivalCountdownWithArrow() {
        val title = buildTrackingNotificationTitle(
            routeNames = listOf("Kiel", "Lübeck", "Hamburg"),
            baseTitle = "Kiel -> Lübeck -> Hamburg",
            countdown = TrackingTitleCountdown(
                kind = TrackingCountdownKind.ARRIVAL,
                seconds = 754,
            ),
            maxLength = 50,
        )
        assertEquals("↓ 12:34 · Kiel -> Lübeck -> Hamburg", title)
    }

    @Test
    fun titleWithCountdown_withoutCountdown_returnsBaseTitle() {
        assertEquals(
            "Kiel -> Lübeck -> Hamburg",
            buildTrackingNotificationTitle(
                routeNames = listOf("Kiel", "Lübeck", "Hamburg"),
                baseTitle = "Kiel -> Lübeck -> Hamburg",
                countdown = TrackingTitleCountdown(),
            ),
        )
    }

    @Test
    fun trackingTitleCountdown_showsDepartureBeforeDepartureAndArrivalAfter() {
        val dep = "2026-05-30T12:30:00"
        val arr = "2026-05-30T16:00:00"
        val journey = journey(dep = dep, arr = arr)
        val tracked = listOf(
            de.openbahn.navigator.data.TrackedJourneyWithJourney(
                entity = de.openbahn.navigator.data.TrackedJourneyEntity(
                    id = "j1",
                    fromName = "A",
                    toName = "B",
                    departureIso = dep,
                    refreshToken = null,
                    journeyJson = "{}",
                    active = true,
                ),
                journey = journey,
            ),
        )
        val beforeDeparture = LocalDateTime.of(2026, 5, 30, 12, 26, 0)
        val afterDeparture = LocalDateTime.of(2026, 5, 30, 13, 0, 0)

        assertEquals(
            TrackingTitleCountdown(kind = TrackingCountdownKind.DEPARTURE, seconds = 240),
            trackingTitleCountdown(tracked, beforeDeparture, true, true),
        )
        assertEquals(
            TrackingTitleCountdown(kind = TrackingCountdownKind.ARRIVAL, seconds = 10_800),
            trackingTitleCountdown(tracked, afterDeparture, true, true),
        )
    }

    private fun journey(dep: String, arr: String): Journey = Journey(
        id = "j1",
        legs = listOf(
            Leg(
                origin = StopEvent("A", scheduledTime = dep),
                destination = StopEvent("B", scheduledTime = arr),
                lineName = "ICE 1",
            ),
        ),
        durationMinutes = 60,
        transfers = 0,
        departure = dep,
        arrival = arr,
    )
}
