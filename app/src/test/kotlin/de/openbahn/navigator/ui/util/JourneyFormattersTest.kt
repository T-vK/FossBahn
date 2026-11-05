package de.openbahn.navigator.ui.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JourneyFormattersTest {
    @Test
    fun formatDurationMinutes_underOneHour() {
        assertEquals("45 min", formatDurationMinutes(45))
    }

    @Test
    fun formatDurationMinutes_overOneHour() {
        assertEquals("1:05", formatDurationMinutes(65))
        assertEquals("2:30", formatDurationMinutes(150))
    }

    @Test
    fun formatJourneyClock_iso() {
        assertEquals("08:00", formatJourneyClock("2026-06-01T08:00:00"))
        assertEquals("17:57", formatJourneyClock("2026-11-05T17:57:00+01:00"))
        assertEquals("17:57", formatJourneyClock("2026-11-05T17:57:00"))
    }

    @Test
    fun journeyDateLabelIfNotToday_returnsNullForToday() {
        val today = java.time.LocalDate.of(2026, 6, 1)
        assertNull(journeyDateLabelIfNotToday("2026-06-01T08:00:00", referenceDate = today))
    }

    @Test
    fun journeyDateLabelIfNotToday_returnsLabelForOtherDay() {
        val today = java.time.LocalDate.of(2026, 6, 1)
        val label = journeyDateLabelIfNotToday("2026-06-02T08:00:00", referenceDate = today)
        assertNotNull(label)
        assertTrue(label!!.contains("2"))
    }
}
