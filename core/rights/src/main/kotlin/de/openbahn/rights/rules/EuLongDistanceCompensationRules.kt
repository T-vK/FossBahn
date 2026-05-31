package de.openbahn.rights.rules

import de.openbahn.rights.model.EntitlementEvent
import de.openbahn.rights.model.EntitlementKind
import de.openbahn.rights.model.LegalCertainty
import de.openbahn.rights.model.PassengerRightsDecisionState
import de.openbahn.rights.model.PlannedTrip
import de.openbahn.rights.model.TicketContext

/**
 * EU Regulation 2021/782 style compensation for long-distance rail (fault-independent).
 * Amounts are percentages of ticket price — price is often unknown in-app.
 */
object EuLongDistanceCompensationRules {
    data class Result(
        val states: Set<PassengerRightsDecisionState>,
        val entitlements: List<EntitlementEvent>,
    )

    fun evaluate(plannedTrip: PlannedTrip, arrivalDelayMinutes: Int): Result {
        if (plannedTrip.ticketContext == TicketContext.DEUTSCHLAND_TICKET) {
            return Result(emptySet(), emptyList())
        }
        if (!plannedTrip.usesLongDistanceRail &&
            plannedTrip.ticketContext != TicketContext.STANDARD_LONG_DISTANCE
        ) {
            return Result(emptySet(), emptyList())
        }
        return when {
            arrivalDelayMinutes >= 120 -> Result(
                setOf(
                    PassengerRightsDecisionState.COMPENSATION_ELIGIBLE,
                    PassengerRightsDecisionState.HIGH_COMPENSATION_ELIGIBLE,
                ),
                listOf(
                    EntitlementEvent(
                        kind = EntitlementKind.EU_LONG_DISTANCE_50_PERCENT,
                        amountEuroCents = null,
                        legalBasis = "EU-VO 2021/782 Art. 19 — ≥ 120 Min. (50 % des Fahrpreises)",
                        certainty = LegalCertainty.AUTOMATIC_STANDARD,
                        userActionRequired = true,
                        summary = "50 % des Fahrpreises (≥ 120 Min. am Ziel)",
                    ),
                ),
            )
            arrivalDelayMinutes >= 60 -> Result(
                setOf(PassengerRightsDecisionState.COMPENSATION_ELIGIBLE),
                listOf(
                    EntitlementEvent(
                        kind = EntitlementKind.EU_LONG_DISTANCE_25_PERCENT,
                        amountEuroCents = null,
                        legalBasis = "EU-VO 2021/782 Art. 19 — 60–119 Min. (25 % des Fahrpreises)",
                        certainty = LegalCertainty.AUTOMATIC_STANDARD,
                        userActionRequired = true,
                        summary = "25 % des Fahrpreises (60–119 Min. am Ziel)",
                    ),
                ),
            )
            else -> Result(setOf(PassengerRightsDecisionState.NORMAL_DELAY), emptyList())
        }
    }
}
