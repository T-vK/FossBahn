package de.openbahn.navigator.tracking

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import de.openbahn.navigator.data.TrackedJourneyEntity
import de.openbahn.navigator.data.TrackedJourneyWithJourney
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrackingNotificationFormatterTest {

    private val strings = object : TrackingNotificationStrings {
        override fun platformSegment(platform: String): String = "Pt. $platform"
        override fun transfers(count: Int): String =
            if (count == 0) "Direct connection" else "$count Transfers"
        override fun multiTitle(count: Int): String = "$count tracked connections"
    }

    private val formatter = TrackingNotificationFormatter(strings)

    @Test
    fun emptyList_returnsNull() {
        assertNull(formatter.format(emptyList()))
    }

    @Test
    fun single_buildsTitleAndBody() {
        val item = tracked(
            from = "Hamburg",
            to = "Berlin",
            legs = listOf(
                leg(
                    platform = "14A-D",
                    depScheduled = "2026-05-30T18:10:00",
                    depPrognosed = "2026-05-30T18:15:00",
                    arrScheduled = "2026-05-30T21:10:00",
                    arrPrognosed = "2026-05-30T21:15:00",
                    lineName = "RE3",
                    lineDetail = "82129",
                ),
                leg(
                    platform = "5",
                    depScheduled = "2026-05-30T21:20:00",
                    arrScheduled = "2026-05-30T22:00:00",
                    lineName = "S1",
                ),
                leg(
                    platform = "8",
                    depScheduled = "2026-05-30T22:05:00",
                    arrScheduled = "2026-05-30T22:30:00",
                    lineName = "U2",
                ),
            ),
        )

        val content = formatter.format(listOf(item))!!

        assertEquals("Hamburg -> Berlin", content.title)
        assertEquals("Pt. 14A-D: 18:15 -> 22:30 | RE3 (82129) | 2 Transfers", content.text)
        assertEquals(listOf(content.text), content.lines)
    }

    @Test
    fun single_directConnectionUsesZeroTransferWording() {
        val item = tracked(
            from = "Hamburg",
            to = "Berlin",
            legs = listOf(
                leg(
                    platform = "14A-D",
                    depScheduled = "2026-05-30T18:15:00",
                    arrScheduled = "2026-05-30T21:15:00",
                    lineName = "ICE",
                    lineDetail = "ICE",
                ),
            ),
        )

        val content = formatter.format(listOf(item))!!

        // lineDetail identical to lineName is not duplicated in parentheses.
        assertEquals("Pt. 14A-D: 18:15 -> 21:15 | ICE | Direct connection", content.text)
    }

    @Test
    fun single_missingPlatform_omitsPlatformSegment() {
        val item = tracked(
            from = "Hamburg",
            to = "Berlin",
            legs = listOf(
                leg(
                    platform = null,
                    depScheduled = "2026-05-30T18:15:00",
                    arrScheduled = "2026-05-30T21:15:00",
                    lineName = "RE3",
                ),
            ),
        )

        val content = formatter.format(listOf(item))!!

        assertEquals("18:15 -> 21:15 | RE3 | Direct connection", content.text)
    }

    @Test
    fun single_skipsWalkingLegForPlatformAndLine() {
        val item = tracked(
            from = "Hamburg",
            to = "Berlin",
            legs = listOf(
                leg(
                    platform = null,
                    depScheduled = "2026-05-30T18:00:00",
                    arrScheduled = "2026-05-30T18:10:00",
                    lineName = null,
                    isWalking = true,
                ),
                leg(
                    platform = "3",
                    depScheduled = "2026-05-30T18:15:00",
                    arrScheduled = "2026-05-30T21:15:00",
                    lineName = "RE3",
                ),
            ),
        )

        val content = formatter.format(listOf(item))!!

        assertEquals("Pt. 3: 18:15 -> 21:15 | RE3 | Direct connection", content.text)
    }

    @Test
    fun multiple_buildsCountTitleAndOneLinePerConnection() {
        val first = tracked(
            from = "Hamburg",
            to = "Berlin",
            legs = listOf(
                leg(
                    platform = "14A-D",
                    depScheduled = "2026-05-30T18:15:00",
                    arrScheduled = "2026-05-30T21:15:00",
                    lineName = "RE3",
                    lineDetail = "82129",
                ),
                leg(
                    platform = "2",
                    depScheduled = "2026-05-30T21:20:00",
                    arrScheduled = "2026-05-30T22:00:00",
                    lineName = "S5",
                ),
            ),
        )
        val second = tracked(
            from = "Köln",
            to = "München",
            legs = listOf(
                leg(
                    platform = null,
                    depScheduled = "2026-05-30T09:00:00",
                    arrScheduled = "2026-05-30T13:30:00",
                    lineName = "ICE 599",
                ),
            ),
        )

        val content = formatter.format(listOf(first, second))!!

        assertEquals("2 tracked connections", content.title)
        assertEquals(
            listOf(
                "Hamburg -> Berlin | Pt. 14A-D: 18:15 -> 22:00 | RE3",
                "Köln -> München | 09:00 -> 13:30 | ICE 599",
            ),
            content.lines,
        )
        assertEquals(content.lines.joinToString("\n"), content.text)
    }

    private fun tracked(from: String, to: String, legs: List<Leg>): TrackedJourneyWithJourney {
        val journey = Journey(
            id = "$from-$to",
            legs = legs,
            durationMinutes = 180,
            transfers = (legs.count { !it.isWalking } - 1).coerceAtLeast(0),
            departure = legs.first().origin.scheduledTime,
            arrival = legs.last().destination.scheduledTime,
        )
        val entity = TrackedJourneyEntity(
            id = journey.id,
            fromName = from,
            toName = to,
            departureIso = journey.departure,
            refreshToken = "token",
            journeyJson = "{}",
            active = true,
        )
        return TrackedJourneyWithJourney(entity = entity, journey = journey)
    }

    private fun leg(
        platform: String?,
        depScheduled: String,
        arrScheduled: String,
        lineName: String?,
        depPrognosed: String? = null,
        arrPrognosed: String? = null,
        lineDetail: String? = null,
        isWalking: Boolean = false,
    ): Leg = Leg(
        origin = StopEvent(
            name = "origin",
            platform = platform,
            scheduledTime = depScheduled,
            prognosedTime = depPrognosed,
        ),
        destination = StopEvent(
            name = "destination",
            scheduledTime = arrScheduled,
            prognosedTime = arrPrognosed,
        ),
        lineName = lineName,
        lineDetail = lineDetail,
        isWalking = isWalking,
    )
}
