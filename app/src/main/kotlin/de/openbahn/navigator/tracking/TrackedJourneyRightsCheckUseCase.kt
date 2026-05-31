package de.openbahn.navigator.tracking

import de.openbahn.model.Journey
import de.openbahn.navigator.data.ClaimDraftRepository
import de.openbahn.navigator.data.UserPreferencesRepository
import de.openbahn.navigator.domain.PassengerRightsRepository
import de.openbahn.rights.engine.PassengerRightsNotificationPolicy
import kotlinx.coroutines.flow.first
class TrackedJourneyRightsCheckUseCase(
    private val passengerRights: PassengerRightsRepository,
    private val claimDrafts: ClaimDraftRepository,
    private val userPreferences: UserPreferencesRepository,
    private val notifier: PassengerRightsNotifier,
) {
    suspend fun evaluateAndNotify(
        trackedId: String,
        journey: Journey,
        minTransferMinutes: Int,
    ) {
        if (!userPreferences.passengerRightsNotificationsEnabled.first()) return
        val assessment = passengerRights.evaluate(
            journey = journey,
            minTransferMinutes = minTransferMinutes,
            isLastConnectionOfDay = false,
            hasPublicAlternativeInWindow = true,
        )
        if (!passengerRights.shouldSurfaceRightsUi(assessment)) return
        val draft = passengerRights.createOrUpdateDraft(assessment)
        val lastNotified = claimDrafts.lastNotifiedAt(draft.id)
        val decision = PassengerRightsNotificationPolicy.evaluate(assessment, lastNotified)
        if (!decision.shouldNotify || decision.notification == null) return
        notifier.show(trackedId, decision.notification)
        claimDrafts.markNotified(draft.id, assessment.evaluatedAtEpochMillis)
    }
}
