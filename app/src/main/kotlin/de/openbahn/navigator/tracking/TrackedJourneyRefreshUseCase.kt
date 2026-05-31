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
    suspend fun refreshJourney(journey: Journey): Journey = refreshWithLiveData(journey)

    suspend fun refreshAllActive(): Int {
        var updated = 0
        repository.getActiveForWorker().forEach { entity ->
            val existing = Json.decodeFromString<Journey>(entity.journeyJson)
            val merged = refreshWithLiveData(existing)
            repository.updateJourney(entity.id, merged)
            updated++
        }
        return updated
    }

    suspend fun refreshAndCheckDelayNotification(
        entityId: String,
        refreshToken: String,
        notificationIncrementMinutes: Int,
    ): Int? {
        val active = repository.getActiveForWorker().firstOrNull { it.id == entityId } ?: return null
        val existing = Json.decodeFromString<Journey>(active.journeyJson)
        val merged = refreshWithLiveData(existing.copy(refreshToken = refreshToken))
        repository.updateJourney(entityId, merged)
        val maxDelay = merged.maxDelayMinutes()
        val decision = DelayNotificationPolicy.evaluate(
            currentDelayMinutes = maxDelay,
            lastNotifiedDelayMinutes = active.lastNotifiedDelayMinutes,
            incrementMinutes = notificationIncrementMinutes,
        )
        if (!decision.shouldNotify) return null
        repository.updateLastNotifiedDelay(entityId, decision.delayMinutes)
        return decision.delayMinutes
    }

    /**
     * DB journey refresh plus station-board enrichment (same pipeline as connection search).
     */
    private suspend fun refreshWithLiveData(journey: Journey): Journey {
        val (from, to) = journey.trackingEndpoints()
        val token = journey.refreshToken?.takeIf { it.isNotBlank() }
        val afterRefresh = if (token != null) {
            client.refreshJourney(token)?.let { journey.withRealtimeFrom(it) } ?: journey
        } else {
            journey
        }
        if (from == null && to == null) return afterRefresh
        return client.enrichJourneysWithRealtime(listOf(afterRefresh), from, to).firstOrNull()
            ?: afterRefresh
    }
}
