package de.openbahn.rights.rules

import de.openbahn.rights.model.DelayEvent
import de.openbahn.rights.model.ExceptionEvent
import de.openbahn.rights.model.ExceptionKind
import de.openbahn.rights.model.LegalCertainty
import de.openbahn.rights.model.LegalDisclaimers
import de.openbahn.rights.model.PassengerRightsDecisionState
import de.openbahn.rights.model.PlannedTrip
import de.openbahn.rights.model.RightsNotificationKind
import de.openbahn.rights.model.RightsNotificationSuggestion
import de.openbahn.rights.model.RouteGraph
import de.openbahn.rights.model.TicketContext

/**
 * Heuristic, legally qualified exceptions — taxi / Fernverkehr fallback.
 * Never marks outcomes as guaranteed entitlements.
 */
object ReplacementTransportRules {
    data class Result(
        val states: Set<PassengerRightsDecisionState>,
        val exceptions: List<ExceptionEvent>,
        val notifications: List<RightsNotificationSuggestion>,
    )

    fun evaluate(
        plannedTrip: PlannedTrip,
        routeGraph: RouteGraph,
        delay: DelayEvent,
    ): Result {
        val states = mutableSetOf<PassengerRightsDecisionState>()
        val exceptions = mutableListOf<ExceptionEvent>()
        val notifications = mutableListOf<RightsNotificationSuggestion>()

        val severeDisruption = delay.anyCancellation ||
            delay.arrivalDelayMinutes >= 60 ||
            delay.missedTransferCount > 0

        if (plannedTrip.isLastConnectionOfDay && severeDisruption) {
            states.add(PassengerRightsDecisionState.LAST_CONNECTION_RISK)
        }

        if (!routeGraph.hasPublicAlternativeInWindow && severeDisruption) {
            states.add(PassengerRightsDecisionState.NO_PUBLIC_TRANSPORT_AVAILABLE)
            notifications.add(
                RightsNotificationSuggestion(
                    kind = RightsNotificationKind.NO_PUBLIC_TRANSPORT,
                    title = "Keine öffentliche Verbindung",
                    body = "Im Zeitfenster scheint keine zumutbare ÖPNV-Alternative verfügbar.",
                ),
            )
        }

        val taxiPlausible = taxiReimbursementPlausible(plannedTrip, routeGraph, delay)
        if (taxiPlausible) {
            states.add(PassengerRightsDecisionState.TAXI_REIMBURSEMENT_POSSIBLE)
            exceptions.add(
                ExceptionEvent(
                    kind = ExceptionKind.TAXI_REIMBURSEMENT,
                    state = PassengerRightsDecisionState.TAXI_REIMBURSEMENT_POSSIBLE,
                    plausibility = LegalCertainty.LEGALLY_PLAUSIBLE,
                    disclaimer = LegalDisclaimers.TAXI,
                    requiresUserConfirmation = true,
                    summary = "Taxikosten möglicherweise erstattungsfähig (Einzelfall)",
                ),
            )
            notifications.add(
                RightsNotificationSuggestion(
                    kind = RightsNotificationKind.TAXI_MAY_BE_REQUIRED,
                    title = "Taxi kann erforderlich werden",
                    body = "Prüfung notwendig — kein automatischer Anspruch. ${LegalDisclaimers.GENERAL}",
                ),
            )
        }

        val fernPlausible = fernverkehrFallbackPlausible(plannedTrip, routeGraph, delay)
        if (fernPlausible) {
            states.add(PassengerRightsDecisionState.FERNVERKEHR_FALLBACK_POSSIBLE)
            exceptions.add(
                ExceptionEvent(
                    kind = ExceptionKind.FERNVERKEHR_FALLBACK,
                    state = PassengerRightsDecisionState.FERNVERKEHR_FALLBACK_POSSIBLE,
                    plausibility = LegalCertainty.LEGALLY_PLAUSIBLE,
                    disclaimer = LegalDisclaimers.FERNVERKEHR,
                    requiresUserConfirmation = true,
                    summary = "Fernverkehr als Eskalation prüfbar (nicht garantiert)",
                ),
            )
            notifications.add(
                RightsNotificationSuggestion(
                    kind = RightsNotificationKind.FERNVERKEHR_FALLBACK_AVAILABLE,
                    title = "ICE/IC-Alternative",
                    body = "Zur Zielerreichung ggf. prüfbar — besonders bei D-Ticket nur Ausnahmefall.",
                ),
            )
        }

        if (states.isEmpty() && delay.arrivalDelayMinutes > 0) {
            states.add(PassengerRightsDecisionState.NORMAL_DELAY)
        }

        return Result(states, exceptions, notifications)
    }

    private fun taxiReimbursementPlausible(
        plannedTrip: PlannedTrip,
        routeGraph: RouteGraph,
        delay: DelayEvent,
    ): Boolean {
        val severe = delay.anyCancellation || delay.arrivalDelayMinutes >= 60
        if (!severe) return false
        val lastConnectionPressure = plannedTrip.isLastConnectionOfDay
        val noAlternative = !routeGraph.hasPublicAlternativeInWindow
        val midnightRisk = delay.destinationUnreachableBeforeMidnight
        return lastConnectionPressure || noAlternative || midnightRisk
    }

    private fun fernverkehrFallbackPlausible(
        plannedTrip: PlannedTrip,
        routeGraph: RouteGraph,
        delay: DelayEvent,
    ): Boolean {
        if (plannedTrip.ticketContext == TicketContext.DEUTSCHLAND_TICKET) {
            val regionalFailure = delay.anyCancellation || delay.missedTransferCount > 0
            val noAlt = !routeGraph.hasPublicAlternativeInWindow
            val sameDayNeed = delay.arrivalDelayMinutes >= 60 || delay.destinationUnreachableBeforeMidnight
            return regionalFailure && noAlt && sameDayNeed
        }
        if (plannedTrip.usesLongDistanceRail) return false
        return delay.anyCancellation &&
            !routeGraph.hasPublicAlternativeInWindow &&
            delay.arrivalDelayMinutes >= 45
    }
}
