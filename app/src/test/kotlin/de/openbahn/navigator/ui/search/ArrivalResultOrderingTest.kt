package de.openbahn.navigator.ui.search

import de.openbahn.model.Journey
import de.openbahn.model.Leg
import de.openbahn.model.StopEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun selectsLatestQualifyingCandidateBeforeFirstResult() {
        val first = journey("first", "2026-05-30T10:00:00")
        val p1 = journey("p1", "2026-05-30T09:51:00")
        val p2 = journey("p2", "2026-05-30T09:58:00")
        val candidate = selectArrivalPrependCandidate(listOf(p1, p2), first, target)
        // Both arrive before the first result and within 10 min of the target; the latest wins.
        assertEquals("p2", candidate?.id)
    }

    @Test
    fun ignoresCandidatesOutsideTheWindow() {
        val first = journey("first", "2026-05-30T10:00:00")
        val early = journey("early", "2026-05-30T09:45:00")
        val candidate = selectArrivalPrependCandidate(listOf(early), first, target)
        // 09:45 is 15 min before the target -> outside the 10-minute window.
        assertNull(candidate)
    }

    @Test
    fun candidateExactlyAtWindowEdgeQualifies() {
        val first = journey("first", "2026-05-30T10:00:00")
        val edge = journey("edge", "2026-05-30T09:50:00")
        val candidate = selectArrivalPrependCandidate(listOf(edge), first, target)
        // 09:50 is exactly 10 min before the target -> still inside the window.
        assertEquals("edge", candidate?.id)
    }

    @Test
    fun ignoresCandidatesArrivingAtOrAfterFirstResult() {
        val first = journey("first", "2026-05-30T09:55:00")
        val same = journey("same", "2026-05-30T09:55:00")
        val later = journey("later", "2026-05-30T09:58:00")
        val candidate = selectArrivalPrependCandidate(listOf(same, later), first, target)
        // Neither arrives strictly before the first result.
        assertNull(candidate)
    }

    @Test
    fun prognosedArrivalTakesPrecedenceOverScheduled() {
        val first = journey("first", "2026-05-30T10:00:00")
        // Scheduled 09:40 but prognosed 09:58 -> treated as 09:58, which qualifies.
        val delayed = journey("delayed", "2026-05-30T09:40:00", arrivalPrognosed = "2026-05-30T09:58:00")
        val candidate = selectArrivalPrependCandidate(listOf(delayed), first, target)
        assertEquals("delayed", candidate?.id)
    }

    @Test
    fun returnsNullForEmptyEarlierPage() {
        val first = journey("first", "2026-05-30T10:00:00")
        assertNull(selectArrivalPrependCandidate(emptyList(), first, target))
    }

    @Test
    fun ignoresCandidateWithSameIdAsFirstResult() {
        val first = journey("first", "2026-05-30T10:00:00")
        val duplicate = journey("first", "2026-05-30T09:58:00")
        assertNull(selectArrivalPrependCandidate(listOf(duplicate), first, target))
    }

    @Test
    fun usesTwoHourWindowWhenFirstResultIsMoreThanTenMinutesLate() {
        val first = journey("first", "2026-05-30T10:15:00")
        val withinTwoHours = journey("within", "2026-05-30T08:30:00")
        val candidate = selectArrivalPrependCandidate(listOf(withinTwoHours), first, target)
        // First result is 15 min after target -> 2 h window; 08:30 is within that and before 10:15.
        assertEquals("within", candidate?.id)
    }

    @Test
    fun twoHourWindowStillRejectsCandidatesBeforeWindowEdge() {
        val first = journey("first", "2026-05-30T10:15:00")
        val tooEarly = journey("tooEarly", "2026-05-30T07:59:00")
        val candidate = selectArrivalPrependCandidate(listOf(tooEarly), first, target)
        // 07:59 is more than 2 h before the 10:00 target.
        assertNull(candidate)
    }

    @Test
    fun keepsTenMinuteWindowWhenFirstResultIsOnlySlightlyLate() {
        val first = journey("first", "2026-05-30T10:05:00")
        val withinTen = journey("within", "2026-05-30T09:58:00")
        val outsideTen = journey("outside", "2026-05-30T09:45:00")
        val candidate = selectArrivalPrependCandidate(listOf(outsideTen, withinTen), first, target)
        // First result is only 5 min late -> still the 10-minute window.
        assertEquals("within", candidate?.id)
    }

    @Test
    fun arrivalPrependWindowMinutes_switchesAtTenMinuteLateThreshold() {
        val target = LocalDateTime.of(2026, 5, 30, 10, 0)
        assertEquals(10L, arrivalPrependWindowMinutes(target, target))
        assertEquals(10L, arrivalPrependWindowMinutes(target.plusMinutes(10), target))
        assertEquals(120L, arrivalPrependWindowMinutes(target.plusMinutes(11), target))
    }
}
