package de.openbahn.navigator.ui.util

import org.junit.jupiter.api.Assertions.assertEquals
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
    }
}
