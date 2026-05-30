package de.openbahn.navigator.tracking

import de.openbahn.api.DbVendoClient
import de.openbahn.model.Journey
import de.openbahn.model.maxDelayMinutes
import de.openbahn.navigator.data.TrackedJourneyRepository

class TrackedJourneyRefreshUseCase(
    private val client: DbVendoClient,
    private val repository: TrackedJourneyRepository,
) {
    suspend fun refreshJourney(journey: Journey): Journey? {
        val token = journey.refreshToken?.takeIf { it.isNotBlank() } ?: return null
        return client.refreshJourney(token)
    }

    suspend fun refreshAllActive(): Int {
        var updated = 0
        repository.getActiveForWorker().forEach { entity ->
            val token = entity.refreshToken?.takeIf { it.isNotBlank() } ?: return@forEach
            val refreshed = client.refreshJourney(token) ?: return@forEach
            repository.updateJourney(entity.id, refreshed)
            updated++
        }
        return updated
    }

    suspend fun refreshAndCheckDelayNotification(
        entityId: String,
        refreshToken: String,
        fromName: String,
        toName: String,
        notifyThresholdMinutes: Int,
    ): Int? {
        val refreshed = client.refreshJourney(refreshToken) ?: return null
        repository.updateJourney(entityId, refreshed)
        val maxDelay = refreshed.maxDelayMinutes()
        return maxDelay.takeIf { it >= notifyThresholdMinutes }
    }
}
