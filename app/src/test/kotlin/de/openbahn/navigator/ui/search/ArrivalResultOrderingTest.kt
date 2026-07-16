package de.openbahn.navigator.ui.search

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ArrivalResultOrderingTest {

    private fun journey(
        id: String,
        arrivalScheduled: String,
        arrivalPrognosed: String? = null,
        departure: String = "2026-05-30T08:00:00",
    ) = Journey(
        id = id,
        legs = listOf(
            Leg(
                origin = StopEvent("A", scheduledTime = departure),
                destination = StopEvent(
                    "B",
                    scheduledTime = arrivalScheduled,
                    prognosedTime = arrivalPrognosed,
                ),
            ),
        ),
        durationMinutes = 60,
        transfers = 0,
        departure = departure,
        arrival = arrivalScheduled,
    )

    private val target = LocalDateTime.of(2026, 5, 30, 10, 0)

    @Test
    fun bestMatchIsLatestArrivalOnOrBeforeTarget() {
        val a = journey("a", "2026-05-30T09:30:00")
        val b = journey("b", "2026-05-30T09:58:00")
        val c = journey("c", "2026-05-30T10:20:00")
        val ordered = orderJourneysByArrival(listOf(c, a, b), target)
        // b (09:58) is the latest arrival on or before 10:00 -> best match first.
        // No preceding connection within 10 min arrives before b except a (09:30, 30 min early),
        // which is outside the 10-minute window, so b stays first.
        assertEquals(listOf("b", "a", "c"), ordered.map { it.id })
    }

    @Test
    fun precedingWithinTenMinutesIsPromotedAheadOfBestMatch() {
        val best = journey("best", "2026-05-30T09:59:00")
        val preceding = journey("preceding", "2026-05-30T09:52:00")
        val later = journey("later", "2026-05-30T10:30:00")
        val ordered = orderJourneysByArrival(listOf(best, preceding, later), target)
        // preceding (09:52) arrives before best (09:59) and is within 10 min of 10:00 -> shown first.
        assertEquals(listOf("preceding", "best", "later"), ordered.map { it.id })
    }

    @Test
    fun precedingOutsideTenMinutesIsNotPromoted() {
        val best = journey("best", "2026-05-30T09:59:00")
        val early = journey("early", "2026-05-30T09:45:00")
        val ordered = orderJourneysByArrival(listOf(early, best), target)
        // early arrives 15 min before target -> outside the window, best stays first.
        assertEquals(listOf("best", "early"), ordered.map { it.id })
    }

    @Test
    fun closestPrecedingIsChosenWhenMultipleQualify() {
        val best = journey("best", "2026-05-30T09:59:00")
        val p1 = journey("p1", "2026-05-30T09:51:00")
        val p2 = journey("p2", "2026-05-30T09:55:00")
        val ordered = orderJourneysByArrival(listOf(best, p1, p2), target)
        // p2 (09:55) is the connection immediately preceding best -> promoted; p1 follows.
        assertEquals(listOf("p2", "best", "p1"), ordered.map { it.id })
    }

    @Test
    fun prognosedArrivalTakesPrecedenceOverScheduled() {
        // Scheduled 09:40 but prognosed 09:58 -> should be treated as 09:58 (the best match).
        val delayed = journey("delayed", "2026-05-30T09:40:00", arrivalPrognosed = "2026-05-30T09:58:00")
        val onTime = journey("onTime", "2026-05-30T09:45:00")
        val ordered = orderJourneysByArrival(listOf(onTime, delayed), target)
        assertEquals("delayed", ordered.first().id)
    }

    @Test
    fun fallsBackToEarliestWhenAllArriveAfterTarget() {
        val a = journey("a", "2026-05-30T10:05:00")
        val b = journey("b", "2026-05-30T10:20:00")
        val c = journey("c", "2026-05-30T10:40:00")
        val ordered = orderJourneysByArrival(listOf(c, b, a), target)
        // Nothing arrives on/before target -> earliest arrival is closest, shown first.
        assertEquals(listOf("a", "b", "c"), ordered.map { it.id })
    }

    @Test
    fun remainingResultsAreChronologicalByArrival() {
        val best = journey("best", "2026-05-30T09:59:00")
        val preceding = journey("preceding", "2026-05-30T09:53:00")
        val late1 = journey("late1", "2026-05-30T11:00:00")
        val late2 = journey("late2", "2026-05-30T10:15:00")
        val ordered = orderJourneysByArrival(listOf(late1, best, late2, preceding), target)
        assertEquals(listOf("preceding", "best", "late2", "late1"), ordered.map { it.id })
    }

    @Test
    fun singleResultIsReturnedUnchanged() {
        val only = journey("only", "2026-05-30T09:00:00")
        assertEquals(listOf("only"), orderJourneysByArrival(listOf(only), target).map { it.id })
    }

    @Test
    fun emptyListIsReturnedUnchanged() {
        assertEquals(emptyList<String>(), orderJourneysByArrival(emptyList(), target).map { it.id })
    }
}
