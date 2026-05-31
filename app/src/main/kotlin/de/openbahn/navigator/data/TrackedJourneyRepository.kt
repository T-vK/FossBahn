package de.openbahn.navigator.data

import androidx.room.withTransaction
import de.openbahn.model.Journey
import de.openbahn.model.Location
import de.openbahn.navigator.tracking.JourneyTrackingCoordinator
import de.openbahn.navigator.ui.util.isIsoLongArrived
import de.openbahn.navigator.ui.util.isJourneyLongArrived
import de.openbahn.navigator.ui.util.parseJourneyDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TrackedJourneyRepository(
    private val dao: TrackedJourneyDao,
    private val database: OpenBahnDatabase,
    private val trackingCoordinator: Lazy<JourneyTrackingCoordinator>,
) {
    fun observeActive(): Flow<List<TrackedJourneyWithJourney>> = dao.observeActive().map { list ->
        list.mapNotNull { entity ->
            val journey = runCatching { Json.decodeFromString<Journey>(entity.journeyJson) }.getOrNull()
                ?: return@mapNotNull null
            if (isJourneyLongArrived(journey) || isIsoLongArrived(entity.departureIso)) {
                return@mapNotNull null
            }
            TrackedJourneyWithJourney(entity = entity, journey = journey)
        }
    }

    suspend fun track(
        journey: Journey,
        fromName: String,
        toName: String,
        fromLocation: Location? = null,
        toLocation: Location? = null,
    ) {
        if (isJourneyLongArrived(journey)) return
        val entity = TrackedJourneyEntity(
            id = journey.id,
            fromName = fromName,
            toName = toName,
            departureIso = journey.departure,
            refreshToken = journey.refreshToken,
            journeyJson = Json.encodeToString(journey),
            fromLocationJson = fromLocation?.let { Json.encodeToString(it) },
            toLocationJson = toLocation?.let { Json.encodeToString(it) },
            lastNotifiedDelayMinutes = null,
            active = true,
        )
        dao.upsert(entity)
        trackingCoordinator.value.onActiveJourneysChanged()
    }

    suspend fun updateLastNotifiedDelay(id: String, delayMinutes: Int) {
        val active = dao.getActiveById(id) ?: return
        dao.upsert(active.copy(lastNotifiedDelayMinutes = delayMinutes))
    }

    suspend fun stopTracking(id: String) {
        dao.deactivate(id)
        trackingCoordinator.value.onActiveJourneysChanged()
    }

    suspend fun getActiveForWorker(): List<TrackedJourneyEntity> =
        dao.getActive().filter { entity ->
            val journey = runCatching { Json.decodeFromString<Journey>(entity.journeyJson) }.getOrNull()
            when {
                journey != null -> !isJourneyLongArrived(journey)
                else -> !isIsoLongArrived(entity.departureIso)
            }
        }

    suspend fun findActiveWithJourney(id: String): TrackedJourneyWithJourney? {
        val entity = dao.getActiveById(id) ?: return null
        val journey = runCatching { Json.decodeFromString<Journey>(entity.journeyJson) }.getOrNull()
            ?: return null
        if (isJourneyLongArrived(journey) || isIsoLongArrived(entity.departureIso)) return null
        return TrackedJourneyWithJourney(entity = entity, journey = journey)
    }

    fun decodeLocation(json: String?): Location? =
        json?.let { runCatching { Json.decodeFromString<Location>(it) }.getOrNull() }

    suspend fun updateJourney(id: String, journey: Journey) {
        val active = dao.getActiveById(id) ?: return
        dao.upsert(
            active.copy(
                journeyJson = Json.encodeToString(journey),
                departureIso = journey.departure,
                refreshToken = journey.refreshToken ?: active.refreshToken,
            ),
        )
    }

    suspend fun updateJourneys(updates: Map<String, Journey>) {
        if (updates.isEmpty()) return
        database.withTransaction {
            updates.forEach { (id, journey) -> updateJourney(id, journey) }
        }
    }

    suspend fun pruneArrived() {
        pruneArrivedInternal()
    }

    internal suspend fun pruneArrivedInternal() {
        dao.getActive().forEach { entity ->
            val journey = runCatching { Json.decodeFromString<Journey>(entity.journeyJson) }.getOrNull()
            val arrived = when {
                journey != null -> isJourneyLongArrived(journey)
                else -> isIsoLongArrived(entity.departureIso)
            }
            if (arrived) dao.deactivate(entity.id)
        }
    }

    fun departureDateTime(entity: TrackedJourneyEntity): java.time.LocalDateTime? =
        parseJourneyDateTime(entity.departureIso)
}

data class TrackedJourneyWithJourney(
    val entity: TrackedJourneyEntity,
    val journey: Journey,
)
