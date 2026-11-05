package de.openbahn.navigator.tracking

import de.openbahn.api.DbVendoClient
import de.openbahn.api.debug.OpenBahnDebugLog
import de.openbahn.model.Journey
import de.openbahn.model.Location
import de.openbahn.model.maxDelayMinutes
import de.openbahn.model.withRealtimeFrom
import de.openbahn.navigator.data.TrackedJourneyEntity
import de.openbahn.navigator.data.TrackedJourneyRepository
import kotlinx.serialization.json.Json

data class RefreshNotificationResult(
    val delayMinutes: Int?,
    val platformChanges: List<PlatformChange>,
)

class TrackedJourneyRefreshUseCase(
    private val client: DbVendoClient,
    private val repository: TrackedJourneyRepository,
    private val rightsCheck: TrackedJourneyRightsCheckUseCase,
    private val platformNotifier: PlatformChangeNotifier,
) {
    suspend fun refreshJourney(journey: Journey): Journey = refreshWithLiveData(journey)

    suspend fun refreshAllActive(): Int {
        val active = repository.getActiveForWorker()
        if (active.isEmpty()) return 0
        active.forEach { entity ->
            val result = refreshTrackedEntity(entity)
            repository.updateJourney(entity.id, result.merged)
            notifyPlatformChanges(entity, result.platformChanges)
        }
        return active.size
    }

    suspend fun refreshAndCheckNotifications(
        entityId: String,
        refreshToken: String,
        notificationIncrementMinutes: Int,
    ): RefreshNotificationResult? {
        val active = repository.getActiveForWorker().firstOrNull { it.id == entityId } ?: return null
        val result = refreshTrackedEntity(active.copy(refreshToken = refreshToken))
        repository.updateJourney(entityId, result.merged)
        val merged = result.merged
        val platformChanges = result.platformChanges
        val maxDelay = merged.maxDelayMinutes()
        val decision = DelayNotificationPolicy.evaluate(
            currentDelayMinutes = maxDelay,
            lastNotifiedDelayMinutes = active.lastNotifiedDelayMinutes,
            incrementMinutes = notificationIncrementMinutes,
        )
        rightsCheck.evaluateAndNotify(
            trackedId = entityId,
            journey = merged,
            minTransferMinutes = 0,
        )
        val delayMinutes = if (decision.shouldNotify) {
            repository.updateLastNotifiedDelay(entityId, decision.delayMinutes)
            decision.delayMinutes
        } else {
            null
        }
        return RefreshNotificationResult(
            delayMinutes = delayMinutes,
            platformChanges = platformChanges,
        )
    }

    /** @deprecated Use [refreshAndCheckNotifications]. */
    suspend fun refreshAndCheckDelayNotification(
        entityId: String,
        refreshToken: String,
        notificationIncrementMinutes: Int,
    ): Int? = refreshAndCheckNotifications(
        entityId = entityId,
        refreshToken = refreshToken,
        notificationIncrementMinutes = notificationIncrementMinutes,
    )?.delayMinutes

    private suspend fun refreshTrackedEntity(entity: TrackedJourneyEntity): RefreshEntityResult {
        val existing = Json.decodeFromString<Journey>(entity.journeyJson)
        val token = entity.refreshToken?.takeIf { it.isNotBlank() }
        val toRefresh = if (token != null) existing.copy(refreshToken = token) else existing
        val storedFrom = entity.fromLocationJson?.let {
            runCatching { Json.decodeFromString<Location>(it) }.getOrNull()
        }
        val storedTo = entity.toLocationJson?.let {
            runCatching { Json.decodeFromString<Location>(it) }.getOrNull()
        }
        val merged = refreshWithLiveData(toRefresh, storedFrom, storedTo)
        return RefreshEntityResult(
            merged = merged,
            platformChanges = PlatformChangeDetector.detect(existing, merged),
        )
    }

    private fun notifyPlatformChanges(entity: TrackedJourneyEntity, changes: List<PlatformChange>) {
        changes.forEach { change ->
            platformNotifier.showChange(entity.id, entity.fromName, entity.toName, change)
        }
    }

    /**
     * DB journey refresh plus station-board enrichment (same pipeline as connection search).
     * Prefers stop IDs on legs; falls back to locations stored when the user tapped Track
     * (needed for mobile-dbnav journeys whose legs historically lacked EVA ids).
     */
    private suspend fun refreshWithLiveData(
        journey: Journey,
        storedFrom: Location? = null,
        storedTo: Location? = null,
    ): Journey {
        val (legFrom, legTo) = journey.trackingEndpoints()
        val from = legFrom ?: storedFrom
        val to = legTo ?: storedTo
        val token = journey.refreshToken?.takeIf { it.isNotBlank() }
        val afterRefresh = if (token != null) {
            runCatching { client.refreshJourney(token) }
                .onFailure { e ->
                    OpenBahnDebugLog.w("Tracking", "refreshJourney failed: ${e.message}")
                }
                .getOrNull()
                ?.let { journey.withRealtimeFrom(it) }
                ?: journey
        } else {
            journey
        }
        if (from == null && to == null) {
            OpenBahnDebugLog.w(
                "Tracking",
                "no board endpoints for journey ${journey.id.take(24)} (legs lack stop ids)",
            )
            return afterRefresh
        }
        return client.enrichJourneysWithRealtime(listOf(afterRefresh), from, to).firstOrNull()
            ?: afterRefresh
    }

    private data class RefreshEntityResult(
        val merged: Journey,
        val platformChanges: List<PlatformChange>,
    )
}
