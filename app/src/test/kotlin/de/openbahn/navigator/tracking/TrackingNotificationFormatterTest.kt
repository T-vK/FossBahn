package de.openbahn.navigator.tracking

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import de.openbahn.navigator.data.TrackedJourneyEntity
import de.openbahn.navigator.data.TrackedJourneyWithJourney
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackingNotificationFormatterTest {

    private val strings = object : TrackingNotificationStrings {
        override fun platformSegment(platform: String): String = "Pt. $platform"
        override fun multiTitle(count: Int): String = "$count tracked connections"
    }

    private val formatter = TrackingNotificationFormatter(strings)

    @Test
    fun emptyList_returnsNull() {
        assertNull(formatter.format(emptyList()))
    }

    @Test
    fun single_buildsStopTimelineWithPlatforms() {
        val item = tracked(
            from = "Hamburg",
            to = "Berlin",
            legs = listOf(
                leg(
                    originName = "Hamburg",
                    destName = "Lübeck",
                    platform = "1",
                    depScheduled = "2026-05-30T12:30:00",
                    depPrognosed = "2026-05-30T12:35:00",
                    arrPlatform = "13",
                    arrScheduled = "2026-05-30T14:50:00",
                    arrPrognosed = "2026-05-30T14:55:00",
                    lineName = "RE3",
                    lineDetail = "12345",
                ),
                leg(
                    originName = "Lübeck",
                    destName = "Berlin",
                    platform = "13",
                    depScheduled = "2026-05-30T15:05:00",
                    arrPlatform = "2",
                    arrScheduled = "2026-05-30T16:02:00",
                    lineName = "S5",
                ),
            ),
        )

        val content = formatter.format(listOf(item), now = at("2026-05-30T12:36:00"))!!

        // Title lists the transfer station chain, not just from/to.
        assertEquals("Hamburg -> Lübeck -> Berlin", content.title)
        assertEquals(
            "12:35 (Pt. 1) -> 14:55 (Pt. 13) -> 16:02 (Pt. 2) | RE3 (12345)",
            content.text.text,
        )
        assertEquals(listOf(content.text), content.lines)
        // Closest stop to now (12:36) is the departure at 12:35; line name is always bold.
        assertEquals(listOf("12:35", "1", "RE3"), boldSubstrings(content.text))
    }

    @Test
    fun single_boldsStopClosestToNow() {
        val item = tracked(
            from = "Hamburg",
            to = "Berlin",
            legs = listOf(
                leg(
                    platform = "1",
                    depScheduled = "2026-05-30T12:30:00",
                    depPrognosed = "2026-05-30T12:35:00",
                    arrPlatform = "13",
                    arrScheduled = "2026-05-30T14:50:00",
                    arrPrognosed = "2026-05-30T14:55:00",
                    lineName = "RE3",
                    lineDetail = "12345",
                ),
                leg(
                    platform = "13",
                    depScheduled = "2026-05-30T15:05:00",
                    arrPlatform = "2",
                    arrScheduled = "2026-05-30T16:02:00",
                    lineName = "S5",
                ),
            ),
        )

        val content = formatter.format(listOf(item), now = at("2026-05-30T14:54:00"))!!

        // Closest stop is the transfer at 14:55 on platform 13.
        assertEquals(listOf("14:55", "13", "RE3"), boldSubstrings(content.text))
        // Bold ranges cover exactly the value, not the parentheses or the "Pt. " prefix.
        val platformStart = content.text.text.indexOf("(Pt. 13)") + "(Pt. ".length
        assertTrue(content.text.boldRanges.any { it.start == platformStart && it.end == platformStart + 2 })
    }

    @Test
    fun single_directConnection_omitsDuplicateDetail() {
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

        val content = formatter.format(listOf(item), now = at("2026-05-30T18:14:00"))!!

        // lineDetail identical to lineName is not duplicated in parentheses.
        assertEquals("18:15 (Pt. 14A-D) -> 21:15 | ICE", content.text.text)
        assertEquals(listOf("18:15", "14A-D", "ICE"), boldSubstrings(content.text))
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

        val content = formatter.format(listOf(item), now = at("2026-05-30T18:14:00"))!!

        assertEquals("18:15 -> 21:15 | RE3", content.text.text)
        assertEquals(listOf("18:15", "RE3"), boldSubstrings(content.text))
    }

    @Test
    fun single_skipsWalkingLegForTimelineAndLine() {
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

        val content = formatter.format(listOf(item), now = at("2026-05-30T18:14:00"))!!

        assertEquals("18:15 (Pt. 3) -> 21:15 | RE3", content.text.text)
        assertEquals(listOf("18:15", "3", "RE3"), boldSubstrings(content.text))
    }

    @Test
    fun multiple_buildsCountTitleAndTwoLinesPerConnection() {
        val first = tracked(
            from = "Hamburg",
            to = "Berlin",
            legs = listOf(
                leg(
                    originName = "Hamburg",
                    destName = "Bremen",
                    platform = "1",
                    depScheduled = "2026-05-30T12:30:00",
                    depPrognosed = "2026-05-30T12:35:00",
                    arrPlatform = "13",
                    arrScheduled = "2026-05-30T14:50:00",
                    arrPrognosed = "2026-05-30T14:55:00",
                    lineName = "RE3",
                    lineDetail = "82129",
                ),
                leg(
                    originName = "Bremen",
                    destName = "Berlin",
                    platform = "13",
                    depScheduled = "2026-05-30T15:05:00",
                    arrPlatform = "5",
                    arrScheduled = "2026-05-30T15:30:00",
                    lineName = "S5",
                ),
            ),
        )
        val second = tracked(
            from = "Köln",
            to = "München",
            legs = listOf(
                leg(
                    originName = "Köln",
                    destName = "München",
                    platform = null,
                    depScheduled = "2026-05-30T09:00:00",
                    arrScheduled = "2026-05-30T13:30:00",
                    lineName = "ICE 599",
                ),
            ),
        )

        val content = formatter.format(listOf(first, second), now = at("2026-05-30T12:36:00"))!!

        assertEquals("2 tracked connections", content.title)
        assertEquals(
            listOf(
                "Hamburg -> Bremen -> Berlin",
                "12:35 (Pt. 1) -> 14:55 (Pt. 13) -> 15:30 (Pt. 5) | RE3",
                "Köln -> München",
                "09:00 -> 13:30 | ICE 599",
            ),
            content.lines.map { it.text },
        )
        // Collapsed text previews the first connection's route + timeline; route lines carry no styling.
        assertEquals(
            "Hamburg -> Bremen -> Berlin\n12:35 (Pt. 1) -> 14:55 (Pt. 13) -> 15:30 (Pt. 5) | RE3",
            content.text.text,
        )
        assertTrue(content.text.boldRanges.isEmpty())
        assertTrue(content.lines[0].boldRanges.isEmpty())
        assertEquals(listOf("12:35", "1", "RE3"), boldSubstrings(content.lines[1]))
        assertTrue(content.lines[2].boldRanges.isEmpty())
        // In the second connection 13:30 is nearer to 12:36 than 09:00.
        assertEquals(listOf("13:30", "ICE 599"), boldSubstrings(content.lines[3]))
    }

    @Test
    fun single_fallsBackToFromToWhenNoRailLegs() {
        val item = tracked(
            from = "Hamburg",
            to = "Berlin",
            legs = listOf(
                leg(
                    originName = "Hamburg",
                    destName = "Berlin",
                    platform = null,
                    depScheduled = "2026-05-30T18:00:00",
                    arrScheduled = "2026-05-30T18:20:00",
                    lineName = null,
                    isWalking = true,
                ),
            ),
        )

        val content = formatter.format(listOf(item), now = at("2026-05-30T18:01:00"))!!

        assertEquals("Hamburg -> Berlin", content.title)
    }

    @Test
    fun routeStationNames_buildsChainAndDedupesConsecutiveTransfers() {
        val journey = journey(
            legs = listOf(
                leg(
                    originName = "Kiel",
                    destName = "Lübeck",
                    platform = "1",
                    depScheduled = "2026-05-30T12:00:00",
                    arrScheduled = "2026-05-30T12:40:00",
                    lineName = "RE",
                ),
                // Walking transfer within Lübeck must not add a duplicate station.
                leg(
                    originName = "Lübeck",
                    destName = "Lübeck",
                    platform = null,
                    depScheduled = "2026-05-30T12:45:00",
                    arrScheduled = "2026-05-30T12:50:00",
                    lineName = null,
                    isWalking = true,
                ),
                leg(
                    originName = "Lübeck",
                    destName = "Hamburg",
                    platform = "3",
                    depScheduled = "2026-05-30T13:00:00",
                    arrScheduled = "2026-05-30T13:45:00",
                    lineName = "RB",
                ),
            ),
        )

        assertEquals(listOf("Kiel", "Lübeck", "Hamburg"), routeStationNames(journey))
    }

    @Test
    fun routeStationNames_emptyWhenNoRailLegs() {
        val journey = journey(
            legs = listOf(
                leg(
                    originName = "Hamburg",
                    destName = "Berlin",
                    platform = null,
                    depScheduled = "2026-05-30T18:00:00",
                    arrScheduled = "2026-05-30T18:20:00",
                    lineName = null,
                    isWalking = true,
                ),
            ),
        )

        assertTrue(routeStationNames(journey).isEmpty())
    }

    @Test
    fun fitRouteTitle_leavesShortTitlesUnchanged() {
        assertEquals("Kiel -> Lübeck -> Hamburg", fitRouteTitle(listOf("Kiel", "Lübeck", "Hamburg")))
    }

    @Test
    fun fitRouteTitle_shortensLongestNameUntilItFits() {
        val names = listOf("Hamburg Airport", "Hamburg Reeperbahn", "Hamburg HBF")

        val fitted = fitRouteTitle(names, maxLength = 50)

        assertTrue(fitted.length <= 50, "expected '$fitted' to fit in 50 chars")
        assertTrue(fitted.contains("..."), "expected an ellipsis in '$fitted'")
        // The shortest name is never touched; the longest ones absorb the truncation.
        assertTrue(fitted.endsWith("Hamburg HBF"), "expected shortest name intact in '$fitted'")
        assertTrue(fitted.startsWith("Hamburg Air"), "expected leading chars kept in '$fitted'")
    }

    @Test
    fun fitRouteTitle_stopsWhenNamesCannotShrinkFurther() {
        // A single very long name with no separators still shrinks toward one kept char + ellipsis.
        val fitted = fitRouteTitle(listOf("Ausgburgerstrasse"), maxLength = 5)

        assertTrue(fitted.length <= 5, "expected '$fitted' to fit in 5 chars")
        assertTrue(fitted.endsWith("..."))
    }

    @Test
    fun combineStyledLines_joinsLinesAndOffsetsBoldRanges() {
        val combined = combineStyledLines(
            listOf(
                StyledNotificationText("Kiel", listOf(BoldRange(0, 4))),
                StyledNotificationText("12:35 RE3", listOf(BoldRange(6, 9))),
            ),
        )

        assertEquals("Kiel\n12:35 RE3", combined.text)
        assertEquals(listOf("Kiel", "RE3"), boldSubstrings(combined))
    }

    private fun boldSubstrings(styled: StyledNotificationText): List<String> =
        styled.boldRanges.map { styled.text.substring(it.start, it.end) }

    private fun at(iso: String): LocalDateTime = LocalDateTime.parse(iso)

    private fun journey(legs: List<Leg>, id: String = "journey"): Journey = Journey(
        id = id,
        legs = legs,
        durationMinutes = 180,
        transfers = (legs.count { !it.isWalking } - 1).coerceAtLeast(0),
        departure = legs.first().origin.scheduledTime,
        arrival = legs.last().destination.scheduledTime,
    )

    private fun tracked(from: String, to: String, legs: List<Leg>): TrackedJourneyWithJourney {
        val journey = journey(legs, id = "$from-$to")
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
        originName: String = "origin",
        destName: String = "destination",
        depPrognosed: String? = null,
        arrPlatform: String? = null,
        arrPrognosed: String? = null,
        lineDetail: String? = null,
        isWalking: Boolean = false,
    ): Leg = Leg(
        origin = StopEvent(
            name = originName,
            platform = platform,
            scheduledTime = depScheduled,
            prognosedTime = depPrognosed,
        ),
        destination = StopEvent(
            name = destName,
            platform = arrPlatform,
            scheduledTime = arrScheduled,
            prognosedTime = arrPrognosed,
        ),
        lineName = lineName,
        lineDetail = lineDetail,
        isWalking = isWalking,
    )
}
