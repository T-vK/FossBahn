package de.openbahn.rights

import de.openbahn.rights.model.PassengerRightsDecisionState
import de.openbahn.rights.model.TicketContext
import de.openbahn.rights.rules.DeutschlandTicketCompensationRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeutschlandTicketCompensationRulesTest {
    @Test
    fun delay60yields150cents() {
        val result = DeutschlandTicketCompensationRules.evaluate(
            ticketContext = TicketContext.DEUTSCHLAND_TICKET,
            journeyId = "j1",
            arrivalDelayMinutes = 65,
            recordedAtEpochMillis = 1_700_000_000_000L,
            existingLedger = null,
            yearMonth = "2026-05",
        )
        assertTrue(PassengerRightsDecisionState.COMPENSATION_ELIGIBLE in result.states)
        assertEquals(150, result.entitlements.single().amountEuroCents)
    }

    @Test
    fun delay120yields250cents() {
        val result = DeutschlandTicketCompensationRules.evaluate(
            ticketContext = TicketContext.DEUTSCHLAND_TICKET,
            journeyId = "j1",
            arrivalDelayMinutes = 130,
            recordedAtEpochMillis = 1_700_000_000_000L,
            existingLedger = null,
            yearMonth = "2026-05",
        )
        assertTrue(PassengerRightsDecisionState.HIGH_COMPENSATION_ELIGIBLE in result.states)
        assertEquals(250, result.entitlements.single().amountEuroCents)
    }

    @Test
    fun monthlyCapAt1225cents() {
        var ledger: de.openbahn.rights.model.MonthlyLedger? = null
        repeat(6) { index ->
            ledger = DeutschlandTicketCompensationRules.evaluate(
                ticketContext = TicketContext.DEUTSCHLAND_TICKET,
                journeyId = "j$index",
                arrivalDelayMinutes = 130,
                recordedAtEpochMillis = index.toLong(),
                existingLedger = ledger,
                yearMonth = "2026-05",
            ).ledgerAfter
        }
        assertTrue(
            ledger!!.totalCompensationEuroCents <= DeutschlandTicketCompensationRules.MONTHLY_CAP_EURO_CENTS,
        )
    }
}
