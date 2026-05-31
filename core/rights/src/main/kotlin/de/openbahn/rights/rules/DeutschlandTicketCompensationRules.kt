package de.openbahn.rights.rules

import de.openbahn.rights.model.EntitlementEvent
import de.openbahn.rights.model.EntitlementKind
import de.openbahn.rights.model.LegalCertainty
import de.openbahn.rights.model.MonthlyLedger
import de.openbahn.rights.model.PassengerRightsDecisionState
import de.openbahn.rights.model.RightsNotificationKind
import de.openbahn.rights.model.RightsNotificationSuggestion
import de.openbahn.rights.model.TicketContext

object DeutschlandTicketCompensationRules {
    const val AMOUNT_60_MIN_EURO_CENTS = 150
    const val AMOUNT_120_MIN_EURO_CENTS = 250
    const val MONTHLY_CAP_EURO_CENTS = 1225
    const val MONTHLY_TICKET_PRICE_EURO_CENTS = 4900

    data class Result(
        val states: Set<PassengerRightsDecisionState>,
        val entitlements: List<EntitlementEvent>,
        val notifications: List<RightsNotificationSuggestion>,
        val ledgerAfter: MonthlyLedger?,
    )

    fun evaluate(
        ticketContext: TicketContext,
        journeyId: String,
        arrivalDelayMinutes: Int,
        recordedAtEpochMillis: Long,
        existingLedger: MonthlyLedger?,
        yearMonth: String,
    ): Result {
        if (ticketContext != TicketContext.DEUTSCHLAND_TICKET) {
            return Result(emptySet(), emptyList(), emptyList(), existingLedger)
        }
        val entitlement = when {
            arrivalDelayMinutes >= 120 -> entitlement120()
            arrivalDelayMinutes >= 60 -> entitlement60()
            else -> return Result(
                setOf(PassengerRightsDecisionState.NORMAL_DELAY),
                emptyList(),
                emptyList(),
                existingLedger,
            )
        }
        val states = buildSet {
            add(PassengerRightsDecisionState.COMPENSATION_ELIGIBLE)
            if (arrivalDelayMinutes >= 120) {
                add(PassengerRightsDecisionState.HIGH_COMPENSATION_ELIGIBLE)
            }
        }
        val incidentAmount = entitlement.amountEuroCents ?: 0
        val ledger = accumulateLedger(
            existing = existingLedger,
            yearMonth = yearMonth,
            journeyId = journeyId,
            recordedAtEpochMillis = recordedAtEpochMillis,
            amountEuroCents = incidentAmount,
            arrivalDelayMinutes = arrivalDelayMinutes,
        )
        val notifications = buildList {
            add(
                RightsNotificationSuggestion(
                    kind = RightsNotificationKind.DELAY_60_THRESHOLD,
                    title = "Deutschlandticket: Verspätungsschwelle",
                    body = "Am Ziel ${arrivalDelayMinutes} Min. Verspätung — " +
                        "Pauschalentschädigung ${formatEuro(incidentAmount)} prüfbar.",
                ),
            )
            if (ledger.capReached) {
                add(
                    RightsNotificationSuggestion(
                        kind = RightsNotificationKind.DTICKET_CAP_WARNING,
                        title = "Monatsdeckel erreicht",
                        body = "Entschädigungen diesen Monat: ${formatEuro(ledger.totalCompensationEuroCents)} " +
                            "(max. ${formatEuro(ledger.capEuroCents)}).",
                    ),
                )
            }
        }
        val cappedEntitlement = if (ledger.capReached && incidentAmount > ledger.remainingEuroCents) {
            entitlement.copy(
                amountEuroCents = ledger.remainingEuroCents,
                summary = entitlement.summary + " (anteilig wegen Monatsdeckel)",
            )
        } else {
            entitlement
        }
        return Result(states, listOf(cappedEntitlement), notifications, ledger)
    }

    private fun entitlement60() = EntitlementEvent(
        kind = EntitlementKind.DTICKET_DELAY_60,
        amountEuroCents = AMOUNT_60_MIN_EURO_CENTS,
        legalBasis = "Deutschlandticket / Nahverkehr — ≥ 60 Min. am Ziel",
        certainty = LegalCertainty.AUTOMATIC_STANDARD,
        userActionRequired = true,
        summary = "1,50 € Pauschalentschädigung (≥ 60 Min.)",
    )

    private fun entitlement120() = EntitlementEvent(
        kind = EntitlementKind.DTICKET_DELAY_120,
        amountEuroCents = AMOUNT_120_MIN_EURO_CENTS,
        legalBasis = "Deutschlandticket / Nahverkehr — ≥ 120 Min. am Ziel",
        certainty = LegalCertainty.AUTOMATIC_STANDARD,
        userActionRequired = true,
        summary = "2,50 € Pauschalentschädigung (≥ 120 Min.)",
    )

    private fun accumulateLedger(
        existing: MonthlyLedger?,
        yearMonth: String,
        journeyId: String,
        recordedAtEpochMillis: Long,
        amountEuroCents: Int,
        arrivalDelayMinutes: Int,
    ): MonthlyLedger {
        val base = existing?.takeIf { it.yearMonth == yearMonth }
        val incidents = base?.incidents.orEmpty() + de.openbahn.rights.model.LedgerIncident(
            journeyId = journeyId,
            recordedAtEpochMillis = recordedAtEpochMillis,
            amountEuroCents = amountEuroCents,
            arrivalDelayMinutes = arrivalDelayMinutes,
        )
        val total = incidents.sumOf { it.amountEuroCents }.coerceAtMost(MONTHLY_CAP_EURO_CENTS)
        return MonthlyLedger(
            yearMonth = yearMonth,
            totalCompensationEuroCents = total,
            capEuroCents = MONTHLY_CAP_EURO_CENTS,
            incidents = incidents,
        )
    }

    private fun formatEuro(cents: Int): String =
        "€%.2f".format(cents / 100.0).replace('.', ',')
}
