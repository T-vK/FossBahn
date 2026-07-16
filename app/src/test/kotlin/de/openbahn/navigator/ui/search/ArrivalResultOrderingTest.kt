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

    private val target = LocalDateTime.of(2026, 5, 30, 18, 0)

    @Test
    fun mergeArrivalSearchPages_keepsChronologicalOrderAcrossPages() {
        val earlier = journey("earlier", "2026-05-30T17:30:00")
        val initial = journey("initial", "2026-05-30T18:00:00")
        val later = journey("later", "2026-05-30T18:15:00")
        val merged = mergeArrivalSearchPages(
            initial = listOf(initial),
            earlier = listOf(earlier),
            later = listOf(later),
        )
        assertEquals(listOf("earlier", "initial", "later"), merged.map { it.id })
    }

    @Test
    fun trim_hidesEarlyResultsAndStartsAtBestMatch() {
        val early1 = journey("early1", "2026-05-30T16:00:00")
        val early2 = journey("early2", "2026-05-30T17:00:00")
        val best = journey("best", "2026-05-30T18:00:00")
        val after = journey("after", "2026-05-30T18:15:00")
        val trimmed = trimArrivalResultsForDisplay(listOf(early1, early2, best, after), target)
        assertEquals(listOf("best", "after"), trimmed.visible.map { it.id })
        assertEquals(listOf("early1", "early2"), trimmed.hidden.map { it.id })
    }

    @Test
    fun trim_keepsQualifyingBufferBeforeBestMatch() {
        val early = journey("early", "2026-05-30T16:00:00")
        val buffer = journey("buffer", "2026-05-30T17:58:00")
        val best = journey("best", "2026-05-30T18:00:00")
        val after = journey("after", "2026-05-30T18:15:00")
        val trimmed = trimArrivalResultsForDisplay(listOf(early, buffer, best, after), target)
        assertEquals(listOf("buffer", "best", "after"), trimmed.visible.map { it.id })
        assertEquals(listOf("early"), trimmed.hidden.map { it.id })
    }

    @Test
    fun trim_ignoresBufferOutsideTenMinuteWindow() {
        val early = journey("early", "2026-05-30T17:30:00")
        val best = journey("best", "2026-05-30T18:00:00")
        val trimmed = trimArrivalResultsForDisplay(listOf(early, best), target)
        assertEquals(listOf("best"), trimmed.visible.map { it.id })
        assertEquals(listOf("early"), trimmed.hidden.map { it.id })
    }

    @Test
    fun trim_usesLaterPageToFindBestMatchWhenInitialResultsAreTooEarly() {
        val initialEarly = journey("initialEarly", "2026-05-30T17:30:00")
        val laterBest = journey("laterBest", "2026-05-30T18:00:00")
        val laterAfter = journey("laterAfter", "2026-05-30T18:15:00")
        val merged = mergeArrivalSearchPages(
            initial = listOf(initialEarly),
            later = listOf(laterBest, laterAfter),
        )
        val trimmed = trimArrivalResultsForDisplay(merged, target)
        assertEquals(listOf("laterBest", "laterAfter"), trimmed.visible.map { it.id })
        assertEquals(listOf("initialEarly"), trimmed.hidden.map { it.id })
    }

    @Test
    fun trim_usesTwoHourWindowWhenBestMatchIsMoreThanTenMinutesLate() {
        val buffer = journey("buffer", "2026-05-30T16:30:00")
        val best = journey("best", "2026-05-30T18:15:00")
        val after = journey("after", "2026-05-30T18:30:00")
        val trimmed = trimArrivalResultsForDisplay(listOf(buffer, best, after), target)
        assertEquals(listOf("buffer", "best", "after"), trimmed.visible.map { it.id })
        assertEquals(emptyList<String>(), trimmed.hidden.map { it.id })
    }

    @Test
    fun trim_twoHourWindowStillRejectsBuffersBeforeWindowEdge() {
        val tooEarly = journey("tooEarly", "2026-05-30T15:59:00")
        val best = journey("best", "2026-05-30T18:15:00")
        val trimmed = trimArrivalResultsForDisplay(listOf(tooEarly, best), target)
        assertEquals(listOf("best"), trimmed.visible.map { it.id })
        assertEquals(listOf("tooEarly"), trimmed.hidden.map { it.id })
    }

    @Test
    fun trim_fallsBackToEarliestWhenAllResultsAreLate() {
        val late1 = journey("late1", "2026-05-30T18:05:00")
        val late2 = journey("late2", "2026-05-30T18:20:00")
        val trimmed = trimArrivalResultsForDisplay(listOf(late2, late1), target)
        assertEquals(listOf("late1", "late2"), trimmed.visible.map { it.id })
    }

    @Test
    fun trim_prognosedArrivalTakesPrecedenceOverScheduled() {
        val best = journey("best", "2026-05-30T18:00:00")
        val delayed = journey("delayed", "2026-05-30T17:40:00", arrivalPrognosed = "2026-05-30T17:58:00")
        val trimmed = trimArrivalResultsForDisplay(listOf(delayed, best), target)
        assertEquals(listOf("delayed", "best"), trimmed.visible.map { it.id })
    }

    @Test
    fun arrivalBufferWindowMinutes_switchesAtTenMinuteLateThreshold() {
        val target = LocalDateTime.of(2026, 5, 30, 10, 0)
        assertEquals(10L, arrivalBufferWindowMinutes(target, target))
        assertEquals(10L, arrivalBufferWindowMinutes(target.plusMinutes(10), target))
        assertEquals(120L, arrivalBufferWindowMinutes(target.plusMinutes(11), target))
    }
}
