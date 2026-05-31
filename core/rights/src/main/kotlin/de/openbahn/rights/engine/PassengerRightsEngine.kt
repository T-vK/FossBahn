package de.openbahn.rights.engine

import de.openbahn.rights.model.PassengerRightsAssessment
import de.openbahn.rights.model.PassengerRightsDecisionState
import de.openbahn.rights.rules.DeutschlandTicketCompensationRules
import de.openbahn.rights.rules.EuLongDistanceCompensationRules
import de.openbahn.rights.rules.ReplacementTransportRules
import de.openbahn.rights.stream.TripEventStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Deterministic rule orchestration: standard entitlements first, then exception heuristics.
 */
object PassengerRightsEngine {
    private val berlinZone = ZoneId.of("Europe/Berlin")
    private val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    fun evaluate(
        stream: TripEventStream,
        existingLedger: de.openbahn.rights.model.MonthlyLedger? = null,
    ): PassengerRightsAssessment {
        val delay = stream.latestDelay
            ?: error("TripEventStream requires at least one DelayEvent")
        val planned = stream.plannedTrip
        val route = stream.routeGraph
        val evaluatedAt = delay.recordedAtEpochMillis
        val yearMonth = Instant.ofEpochMilli(evaluatedAt).atZone(berlinZone).format(yearMonthFormatter)

        val dt = DeutschlandTicketCompensationRules.evaluate(
            ticketContext = planned.ticketContext,
            journeyId = planned.journeyId,
            arrivalDelayMinutes = delay.arrivalDelayMinutes,
            recordedAtEpochMillis = evaluatedAt,
            existingLedger = existingLedger,
            yearMonth = yearMonth,
        )
        val eu = EuLongDistanceCompensationRules.evaluate(planned, delay.arrivalDelayMinutes)
        val replacement = ReplacementTransportRules.evaluate(planned, route, delay)

        val states = buildSet {
            addAll(dt.states)
            addAll(eu.states)
            addAll(replacement.states)
        }.ifEmpty { setOf(PassengerRightsDecisionState.NORMAL_DELAY) }

        val entitlements = dt.entitlements + eu.entitlements
        val notifications = dt.notifications + replacement.notifications

        return PassengerRightsAssessment(
            plannedTrip = planned,
            routeGraph = route,
            delayEvent = delay,
            decisionStates = states,
            entitlements = entitlements,
            exceptions = replacement.exceptions,
            notifications = notifications,
            monthlyLedgerSnapshot = dt.ledgerAfter,
            evaluatedAtEpochMillis = evaluatedAt,
        )
    }

}
