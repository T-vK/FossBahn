package de.openbahn.navigator.ui.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TransferTimeBumpTest {

    @Test
    fun suggestsPresetsAboveScheduledTransfer() {
        assertEquals(listOf(15, 20, 30), transferTimeBumpOptions(scheduledTransferMinutes = 10, currentMinTransferMinutes = null))
    }

    @Test
    fun respectsCurrentFilterFloor() {
        assertEquals(listOf(20, 30), transferTimeBumpOptions(scheduledTransferMinutes = 8, currentMinTransferMinutes = 15))
    }

    @Test
    fun emptyWhenAlreadyAtMaxPreset() {
        assertEquals(emptyList<Int>(), transferTimeBumpOptions(scheduledTransferMinutes = 35, currentMinTransferMinutes = 30))
    }
}
