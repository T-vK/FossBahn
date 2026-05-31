package de.openbahn.rights.model

import kotlinx.serialization.Serializable

/** High-level decision states emitted by the rights engine (may overlap). */
@Serializable
enum class PassengerRightsDecisionState {
    NORMAL_DELAY,
    COMPENSATION_ELIGIBLE,
    HIGH_COMPENSATION_ELIGIBLE,
    LAST_CONNECTION_RISK,
    NO_PUBLIC_TRANSPORT_AVAILABLE,
    TAXI_REIMBURSEMENT_POSSIBLE,
    FERNVERKEHR_FALLBACK_POSSIBLE,
}

/** Ticket context drives which rule sets apply. */
@Serializable
enum class TicketContext {
    STANDARD_LONG_DISTANCE,
    STANDARD_REGIONAL,
    DEUTSCHLAND_TICKET,
}

/** Certainty of a legal outcome — never implies a guaranteed claim. */
@Serializable
enum class LegalCertainty {
    /** Fixed statutory amounts (e.g. Deutschlandticket 1,50 € / 2,50 €). */
    AUTOMATIC_STANDARD,
    /** Plausible under EVO / BGB if individual requirements are met. */
    LEGALLY_PLAUSIBLE,
    /** Insufficient data or borderline — user must review. */
    REQUIRES_REVIEW,
}

@Serializable
data class PlannedTrip(
    val journeyId: String,
    val departureIso: String,
    val arrivalIso: String,
    val fromName: String,
    val toName: String,
    val ticketContext: TicketContext,
    val railLegCount: Int,
    /** Heuristic or user-provided: last reasonable connection of the day on this relation. */
    val isLastConnectionOfDay: Boolean = false,
    val usesLongDistanceRail: Boolean = false,
    val deutschlandTicketMarkedValid: Boolean? = null,
)

@Serializable
data class RouteAlternative(
    val description: String,
    val reachableBeforeMidnight: Boolean,
)

@Serializable
data class RouteGraph(
    val plannedTrip: PlannedTrip,
    val alternatives: List<RouteAlternative> = emptyList(),
    val hasPublicAlternativeInWindow: Boolean = true,
)

@Serializable
data class DelayEvent(
    val recordedAtEpochMillis: Long,
    val arrivalDelayMinutes: Int,
    val maxEnRouteDelayMinutes: Int,
    val anyCancellation: Boolean,
    val missedTransferCount: Int,
    val destinationUnreachableBeforeMidnight: Boolean,
)

@Serializable
enum class EntitlementKind {
    DTICKET_DELAY_60,
    DTICKET_DELAY_120,
    EU_LONG_DISTANCE_25_PERCENT,
    EU_LONG_DISTANCE_50_PERCENT,
}

@Serializable
data class EntitlementEvent(
    val kind: EntitlementKind,
    val amountEuroCents: Int?,
    val legalBasis: String,
    val certainty: LegalCertainty,
    val userActionRequired: Boolean,
    val summary: String,
)

@Serializable
enum class ExceptionKind {
    TAXI_REIMBURSEMENT,
    FERNVERKEHR_FALLBACK,
}

@Serializable
data class ExceptionEvent(
    val kind: ExceptionKind,
    val state: PassengerRightsDecisionState,
    val plausibility: LegalCertainty,
    val disclaimer: String,
    val requiresUserConfirmation: Boolean,
    val summary: String,
)

@Serializable
data class LedgerIncident(
    val journeyId: String,
    val recordedAtEpochMillis: Long,
    val amountEuroCents: Int,
    val arrivalDelayMinutes: Int,
)

@Serializable
data class MonthlyLedger(
    val yearMonth: String,
    val totalCompensationEuroCents: Int,
    val capEuroCents: Int,
    val incidents: List<LedgerIncident>,
) {
    val capReached: Boolean get() = totalCompensationEuroCents >= capEuroCents
    val remainingEuroCents: Int get() = (capEuroCents - totalCompensationEuroCents).coerceAtLeast(0)
}

@Serializable
enum class RightsNotificationKind {
    DELAY_60_THRESHOLD,
    DTICKET_CAP_WARNING,
    NO_PUBLIC_TRANSPORT,
    TAXI_MAY_BE_REQUIRED,
    FERNVERKEHR_FALLBACK_AVAILABLE,
}

@Serializable
data class RightsNotificationSuggestion(
    val kind: RightsNotificationKind,
    val title: String,
    val body: String,
)

@Serializable
data class PassengerRightsAssessment(
    val plannedTrip: PlannedTrip,
    val routeGraph: RouteGraph,
    val delayEvent: DelayEvent,
    val decisionStates: Set<PassengerRightsDecisionState>,
    val entitlements: List<EntitlementEvent>,
    val exceptions: List<ExceptionEvent>,
    val notifications: List<RightsNotificationSuggestion>,
    val monthlyLedgerSnapshot: MonthlyLedger?,
    val evaluatedAtEpochMillis: Long,
)

@Serializable
enum class ClaimDraftStatus {
    DRAFT,
    USER_CONFIRMED,
    SENT,
}

@Serializable
data class ClaimDraft(
    val id: String,
    val journeyId: String,
    val createdAtEpochMillis: Long,
    val status: ClaimDraftStatus,
    val assessmentJson: String,
    val subject: String,
    val bodyText: String,
    val recipientEmail: String? = null,
)
