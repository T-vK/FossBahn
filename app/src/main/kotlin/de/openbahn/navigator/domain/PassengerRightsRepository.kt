package de.openbahn.navigator.domain

import de.openbahn.model.Journey
import de.openbahn.navigator.data.ClaimDraftRepository
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.rights.claims.ClaimDraftBuilder
import de.openbahn.rights.engine.PassengerRightsEngine
import de.openbahn.rights.journey.JourneyRightsAdapter
import de.openbahn.rights.model.ClaimDraft
import de.openbahn.rights.model.PassengerRightsAssessment
import de.openbahn.rights.model.PassengerRightsDecisionState
import de.openbahn.rights.stream.TripEventStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PassengerRightsRepository(
    private val claimDrafts: ClaimDraftRepository,
    private val userPreferences: UserPreferencesRepository,
) {
    private val berlinZone = ZoneId.of("Europe/Berlin")
    private val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    suspend fun evaluate(
        journey: Journey,
        minTransferMinutes: Int = 0,
        isLastConnectionOfDay: Boolean = false,
        hasPublicAlternativeInWindow: Boolean = true,
    ): PassengerRightsAssessment {
        val ownsDticket = userPreferences.deutschlandTicketConnectionsOnly.first()
        val ticketContext = JourneyRightsAdapter.resolveTicketContext(
            journey = journey,
            userOwnsDeutschlandTicket = ownsDticket,
        )
        val stream = JourneyRightsAdapter.toTripEventStream(
            journey = journey,
            ticketContext = ticketContext,
            minTransferMinutes = minTransferMinutes,
            isLastConnectionOfDay = isLastConnectionOfDay,
            hasPublicAlternativeInWindow = hasPublicAlternativeInWindow,
        )
        val yearMonth = yearMonth(stream)
        val ledger = claimDrafts.loadMonthlyLedger(yearMonth)
        return PassengerRightsEngine.evaluate(stream, ledger).also { assessment ->
            assessment.monthlyLedgerSnapshot?.let { claimDrafts.saveMonthlyLedger(it) }
        }
    }

    fun observeClaimDrafts(): Flow<List<ClaimDraft>> = claimDrafts.observeDrafts()

    suspend fun createOrUpdateDraft(assessment: PassengerRightsAssessment): ClaimDraft {
        val draft = ClaimDraftBuilder.createDraft(assessment)
        claimDrafts.saveDraft(draft)
        return draft
    }

    suspend fun shouldSurfaceRightsUi(assessment: PassengerRightsAssessment): Boolean =
        assessment.decisionStates.any { it != PassengerRightsDecisionState.NORMAL_DELAY }

    private fun yearMonth(stream: TripEventStream): String {
        val millis = stream.latestDelay?.recordedAtEpochMillis ?: System.currentTimeMillis()
        return Instant.ofEpochMilli(millis).atZone(berlinZone).format(yearMonthFormatter)
    }
}
