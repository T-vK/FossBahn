package de.openbahn.navigator.tracking

import de.openbahn.api.DbVendoClient
import de.openbahn.model.Journey
import de.openbahn.model.maxDelayMinutes
import de.openbahn.model.withRealtimeFrom
import de.openbahn.navigator.data.TrackedJourneyRepository
import kotlinx.serialization.json.Json

class TrackedJourneyRefreshUseCase(
    private val client: DbVendoClient,
    private val repository: TrackedJourneyRepository,
) {
    suspend fun refreshJourney(journey: Journey): Journey? {
        val token = journey.refreshToken?.takeIf { it.isNotBlank() } ?: return null
        val refreshed = client.refreshJourney(token) ?: return null
        return journey.withRealtimeFrom(refreshed)
    }

    suspend fun refreshAllActive(): Int {
        var updated = 0
        repository.getActiveForWorker().forEach { entity ->
            val token = entity.refreshToken?.takeIf { it.isNotBlank() } ?: return@forEach
            val refreshed = client.refreshJourney(token) ?: return@forEach
            val existing = Json.decodeFromString<Journey>(entity.journeyJson)
            repository.updateJourney(entity.id, existing.withRealtimeFrom(refreshed))
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
        val active = repository.getActiveForWorker().firstOrNull { it.id == entityId } ?: return null
        val existing = Json.decodeFromString<Journey>(active.journeyJson)
        val merged = existing.withRealtimeFrom(refreshed)
        repository.updateJourney(entityId, merged)
        val maxDelay = merged.maxDelayMinutes()
        return maxDelay.takeIf { it >= notifyThresholdMinutes }
    }
}
